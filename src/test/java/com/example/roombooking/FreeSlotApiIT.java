package com.example.roombooking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.example.roombooking.booking.Booking;
import com.example.roombooking.booking.BookingRepository;
import com.example.roombooking.error.ApiError;
import com.example.roombooking.room.Room;
import com.example.roombooking.room.RoomRepository;
import com.example.roombooking.slots.FreeSlotResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.util.UriComponentsBuilder;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "booking.seed-rooms=false",
        "spring.datasource.url=jdbc:h2:mem:freeslotsit;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
})
class FreeSlotApiIT {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private Room aspen;
    private Room birch;
    private Room coop;
    private LocalDate day;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();
        aspen = roomRepository.save(new Room("Aspen", 8, true));
        birch = roomRepository.save(new Room("Birch", 8, true));
        coop = roomRepository.save(new Room("Coop", 1, true));
        day = LocalDate.now().plusDays(7);
    }

    @Test
    void acceptanceCommonFreeSlotsAcrossRooms() {
        insert(aspen, "ann@example.com", "10:00", "11:00", "ann@example.com");
        insert(birch, "ann@example.com", "09:30", "10:30", "ann@example.com");
        insert(aspen, "bob@example.com", "13:00", "14:00", "bob@example.com");
        insert(birch, "bob@example.com", "13:15", "13:45", "bob@example.com");

        List<FreeSlotResponse> slots = freeSlots(30, "ann@example.com", "bob@example.com");

        assertThat(slots).extracting(FreeSlotApiIT::timeRange)
                .containsExactly("09:00-09:30", "11:00-13:00", "14:00-18:00");
        assertThat(slots).allSatisfy(slot -> assertThat(slot.rooms())
                .extracting(FreeSlotResponse.RoomSuggestion::name)
                .containsExactlyInAnyOrder("Aspen", "Birch"));
    }

    @Test
    void cancelledBookingsDoNotBlockAttendeesOrRooms() {
        Booking cancelled = insert(aspen, "ann@example.com", "10:00", "11:00", "ann@example.com");
        rest.exchange("/api/bookings/" + cancelled.getId(), HttpMethod.DELETE, null, Void.class);

        List<FreeSlotResponse> slots = freeSlots(540, "ann@example.com");

        assertThat(slots).extracting(FreeSlotApiIT::timeRange).containsExactly("09:00-18:00");
        assertThat(slots.get(0).rooms()).extracting(FreeSlotResponse.RoomSuggestion::name)
                .containsExactlyInAnyOrder("Aspen", "Birch", "Coop");
    }

    @Test
    void roomsBusyForPartOfASlotAreExcluded() {
        insert(aspen, "other@example.com", "10:00", "11:00", "other@example.com");

        List<FreeSlotResponse> slots = freeSlots(540, "ann@example.com");

        assertThat(slots).extracting(FreeSlotApiIT::timeRange).containsExactly("09:00-18:00");
        assertThat(slots.get(0).rooms()).extracting(FreeSlotResponse.RoomSuggestion::name)
                .containsExactlyInAnyOrder("Birch", "Coop");
    }

    @Test
    void validatesQueryParameters() {
        ResponseEntity<ApiError> zeroDuration = rest.getForEntity(
                "/api/free-slots?date=" + day + "&durationMinutes=0&attendees=ann@example.com", ApiError.class);
        assertThat(zeroDuration.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(zeroDuration.getBody().fieldErrors()).containsKey("durationMinutes");

        ResponseEntity<ApiError> missingAttendees = rest.getForEntity(
                "/api/free-slots?date=" + day + "&durationMinutes=30", ApiError.class);
        assertThat(missingAttendees.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<ApiError> badDate = rest.getForEntity(
                "/api/free-slots?date=oops&durationMinutes=30&attendees=ann@example.com", ApiError.class);
        assertThat(badDate.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private List<FreeSlotResponse> freeSlots(int durationMinutes, String... attendees) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromPath("/api/free-slots")
                .queryParam("date", day)
                .queryParam("durationMinutes", durationMinutes)
                .queryParam("attendees", (Object[]) attendees);
        ResponseEntity<FreeSlotResponse[]> response = rest.getForEntity(builder.toUriString(),
                FreeSlotResponse[].class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return List.of(response.getBody());
    }

    private Booking insert(Room room, String organiser, String start, String end, String... attendees) {
        Set<String> attendeeEmails = new LinkedHashSet<>(List.of(attendees));
        return bookingRepository.save(new Booking(room, "Meeting", organiser, attendeeEmails,
                day.atTime(LocalTime.parse(start)), day.atTime(LocalTime.parse(end)), LocalDateTime.now()));
    }

    private static String timeRange(FreeSlotResponse slot) {
        return slot.start().toLocalTime() + "-" + slot.end().toLocalTime();
    }
}
