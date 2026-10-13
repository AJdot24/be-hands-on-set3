package com.example.roombooking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.example.roombooking.booking.BookingRepository;
import com.example.roombooking.booking.dto.BookingResponse;
import com.example.roombooking.error.ApiError;
import com.example.roombooking.room.Room;
import com.example.roombooking.room.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "booking.seed-rooms=false",
        "spring.datasource.url=jdbc:h2:mem:bookingit;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
})
class BookingApiIT {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private Room room;
    private LocalDate day;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();
        room = roomRepository.save(new Room("Test Room", 8, true));
        day = LocalDate.now().plusDays(7);
    }

    @Test
    void overlappingBookingIsRejectedAndNamesTheClashThenBackToBackSucceeds() {
        ResponseEntity<BookingResponse> first = create(body(at(10, 0), at(11, 0)));
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ApiError> clash = rest.postForEntity("/api/bookings",
                body(at(10, 30), at(11, 30)), ApiError.class);
        assertThat(clash.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(clash.getBody().message()).contains("already booked");
        assertThat(clash.getBody().conflictingBookingId()).isEqualTo(first.getBody().id());

        ResponseEntity<BookingResponse> backToBack = create(body(at(11, 0), at(12, 0)));
        assertThat(backToBack.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void bookingCoveringAnExistingOneIsRejected() {
        assertThat(create(body(at(10, 0), at(10, 30))).getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ApiError> clash = rest.postForEntity("/api/bookings",
                body(at(9, 0), at(12, 0)), ApiError.class);

        assertThat(clash.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void cancellingABookingFreesTheSlot() {
        BookingResponse created = create(body(at(10, 0), at(11, 0))).getBody();

        ResponseEntity<Void> cancelled = rest.exchange("/api/bookings/" + created.id(),
                HttpMethod.DELETE, null, Void.class);
        assertThat(cancelled.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        BookingResponse fetched = rest.getForEntity("/api/bookings/" + created.id(), BookingResponse.class).getBody();
        assertThat(fetched.status()).isEqualTo("CANCELLED");

        assertThat(create(body(at(10, 30), at(11, 30))).getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    @Test
    void invalidBookingReturns400WithMessagePerField() {
        Map<String, Object> invalid = body(at(11, 0), at(10, 0));
        invalid.put("attendees", List.of("b@example.com", "c@example.com", "d@example.com", "e@example.com",
                "f@example.com", "g@example.com", "h@example.com", "i@example.com"));

        ResponseEntity<ApiError> response = rest.postForEntity("/api/bookings", invalid, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().fieldErrors()).containsKeys("end", "attendees");
        assertThat(response.getBody().message()).isEqualTo("Validation failed");
    }

    @Test
    void bookingInThePastIsRejected() {
        LocalDateTime yesterday = day.minusDays(7).atTime(10, 0);

        ResponseEntity<ApiError> response = rest.postForEntity("/api/bookings",
                body(yesterday, yesterday.plusHours(1)), ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(response.getBody().fieldErrors().get("start")).contains("past");
    }

    @Test
    void unknownRoomReturns404() {
        Map<String, Object> request = body(at(10, 0), at(11, 0));
        request.put("roomId", 99999L);

        ResponseEntity<ApiError> response = rest.postForEntity("/api/bookings", request, ApiError.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody().message()).contains("Room 99999");
    }

    @Test
    void unknownBookingReturns404() {
        assertThat(rest.getForEntity("/api/bookings/99999", ApiError.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.exchange("/api/bookings/99999", HttpMethod.DELETE, null, ApiError.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void roomScheduleIsTimeOrderedAndSkipsCancelledBookings() {
        create(body(at(14, 0), at(15, 0)));
        create(body(at(9, 0), at(10, 0)));
        BookingResponse middle = create(body(at(11, 0), at(12, 0))).getBody();

        rest.exchange("/api/bookings/" + middle.id(), HttpMethod.DELETE, null, Void.class);

        ResponseEntity<BookingResponse[]> schedule = rest.getForEntity(
                "/api/rooms/" + room.getId() + "/bookings?date=" + day, BookingResponse[].class);

        assertThat(schedule.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(schedule.getBody()).extracting(BookingResponse::start)
                .containsExactly(day.atTime(9, 0), day.atTime(14, 0));
        assertThat(schedule.getBody()).allSatisfy(booking -> assertThat(booking.status()).isEqualTo("ACTIVE"));
    }

    @Test
    void unknownRoomScheduleReturns404AndBadDateReturns400() {
        assertThat(rest.getForEntity("/api/rooms/99999/bookings?date=" + day, ApiError.class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(rest.getForEntity("/api/rooms/" + room.getId() + "/bookings?date=not-a-date", ApiError.class)
                .getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private ResponseEntity<BookingResponse> create(Map<String, Object> body) {
        return rest.postForEntity("/api/bookings", body, BookingResponse.class);
    }

    private Map<String, Object> body(LocalDateTime start, LocalDateTime end) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roomId", room.getId());
        body.put("title", "Standup");
        body.put("organiserEmail", "ann@example.com");
        body.put("attendees", List.of("bob@example.com"));
        body.put("start", start.toString());
        body.put("end", end.toString());
        return body;
    }

    private LocalDateTime at(int hour, int minute) {
        return day.atTime(hour, minute);
    }
}
