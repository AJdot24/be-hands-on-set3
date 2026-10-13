package com.example.roombooking.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import com.example.roombooking.booking.dto.BookingResponse;
import com.example.roombooking.booking.dto.CreateBookingRequest;
import com.example.roombooking.error.BadRequestException;
import com.example.roombooking.error.ConflictException;
import com.example.roombooking.error.NotFoundException;
import com.example.roombooking.room.Room;
import com.example.roombooking.room.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 5, 8, 0);

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private RoomRepository roomRepository;

    private BookingService bookingService;
    private Room room;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        bookingService = new BookingService(bookingRepository, roomRepository, clock);
        room = new Room("Aspen", 4, true);
        ReflectionTestUtils.setField(room, "id", 1L);
    }

    @Test
    void savesNormalisedBookingWithOrganiserIncluded() {
        givenRoomExists();
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> {
            Booking saved = invocation.getArgument(0);
            ReflectionTestUtils.setField(saved, "id", 42L);
            return saved;
        });

        BookingResponse response = bookingService.createBooking(
                request(day(10, 0), day(11, 0), List.of("Bob@Example.com", "bob@example.com")));

        assertThat(response.id()).isEqualTo(42L);
        assertThat(response.roomName()).isEqualTo("Aspen");
        assertThat(response.organiserEmail()).isEqualTo("ann@example.com");
        assertThat(response.attendees()).containsExactly("ann@example.com", "bob@example.com");
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.status()).isEqualTo("ACTIVE");
    }

    @Test
    void rejectsBookingThatEndsBeforeItStarts() {
        givenRoomExists();

        assertThatThrownBy(() -> bookingService.createBooking(request(day(11, 0), day(10, 30), null)))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getFieldErrors())
                        .containsKey("end"));
    }

    @Test
    void rejectsBookingSpanningMultipleDays() {
        givenRoomExists();

        assertThatThrownBy(() -> bookingService.createBooking(request(day(17, 0), dayPlus(1, 9, 0), null)))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getFieldErrors())
                        .containsKey("start"));
    }

    @Test
    void rejectsBookingStartingBeforeWorkingHours() {
        givenRoomExists();

        assertThatThrownBy(() -> bookingService.createBooking(request(day(8, 45), day(9, 30), null)))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getFieldErrors())
                        .containsEntry("start", "must be at or after 09:00"));
    }

    @Test
    void rejectsBookingEndingAfterWorkingHours() {
        givenRoomExists();

        assertThatThrownBy(() -> bookingService.createBooking(request(day(17, 30), day(18, 15), null)))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getFieldErrors())
                        .containsEntry("end", "must be at or before 18:00"));
    }

    @Test
    void rejectsTimesOffTheFifteenMinuteGrid() {
        givenRoomExists();

        assertThatThrownBy(() -> bookingService.createBooking(request(day(10, 0), day(11, 7), null)))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getFieldErrors())
                        .containsKey("end"));
    }

    @Test
    void rejectsBookingLongerThanFourHours() {
        givenRoomExists();

        assertThatThrownBy(() -> bookingService.createBooking(request(day(9, 0), day(13, 15), null)))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getFieldErrors())
                        .containsEntry("end", "must be at most 4 hours after start"));
    }

    @Test
    void rejectsBookingsInThePast() {
        givenRoomExists();
        LocalDateTime yesterday = LocalDateTime.of(2026, 1, 4, 10, 0);

        assertThatThrownBy(() -> bookingService.createBooking(request(yesterday, yesterday.plusHours(1), null)))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getFieldErrors().get("start"))
                        .contains("past"));
    }

    @Test
    void rejectsMoreAttendeesThanRoomCapacity() {
        givenRoomExists();

        assertThatThrownBy(() -> bookingService.createBooking(request(day(10, 0), day(11, 0),
                List.of("b@example.com", "c@example.com", "d@example.com", "e@example.com"))))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getFieldErrors().get("attendees"))
                        .contains("exceeds room capacity (4)"));
    }

    @Test
    void collectsMultipleFieldErrorsInOneResponse() {
        givenRoomExists();

        assertThatThrownBy(() -> bookingService.createBooking(request(day(8, 45), day(18, 30), null)))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getFieldErrors())
                        .containsKeys("start", "end"));
    }

    @Test
    void rejectsOverlapAndNamesTheClashingBooking() {
        givenRoomExists();
        Booking clash = new Booking(room, "Standup", "x@example.com", Set.of("x@example.com"),
                day(10, 0), day(11, 0), NOW);
        ReflectionTestUtils.setField(clash, "id", 7L);
        when(bookingRepository.findOverlapping(1L, BookingStatus.ACTIVE, day(10, 30), day(11, 30)))
                .thenReturn(List.of(clash));

        assertThatThrownBy(() -> bookingService.createBooking(request(day(10, 30), day(11, 30), null)))
                .isInstanceOf(ConflictException.class)
                .hasMessageContaining("booking 7")
                .hasMessageContaining("Standup")
                .satisfies(ex -> assertThat(((ConflictException) ex).getConflictingBookingId()).isEqualTo(7L));
    }

    @Test
    void allowsBackToBackBookings() {
        givenRoomExists();
        when(bookingRepository.findOverlapping(1L, BookingStatus.ACTIVE, day(11, 0), day(12, 0)))
                .thenReturn(List.of());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        BookingResponse response = bookingService.createBooking(request(day(11, 0), day(12, 0), null));

        assertThat(response.start()).isEqualTo(day(11, 0));
        assertThat(response.end()).isEqualTo(day(12, 0));
    }

    @Test
    void rejectsUnknownRoom() {
        when(roomRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(
                new CreateBookingRequest(99L, "Standup", "ann@example.com", null, day(10, 0), day(11, 0))))
                .isInstanceOf(NotFoundException.class)
                .hasMessageContaining("Room 99");
    }

    private void givenRoomExists() {
        when(roomRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(room));
    }

    private CreateBookingRequest request(LocalDateTime start, LocalDateTime end, List<String> attendees) {
        return new CreateBookingRequest(1L, "Standup", "Ann@Example.com", attendees, start, end);
    }

    private static LocalDateTime day(int hour, int minute) {
        return LocalDateTime.of(2026, 1, 6, hour, minute);
    }

    private static LocalDateTime dayPlus(int plusDays, int hour, int minute) {
        return LocalDateTime.of(2026, 1, 6, hour, minute).plusDays(plusDays);
    }
}
