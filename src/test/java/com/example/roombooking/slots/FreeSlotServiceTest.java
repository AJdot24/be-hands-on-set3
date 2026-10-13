package com.example.roombooking.slots;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;
import java.util.Set;

import com.example.roombooking.booking.Booking;
import com.example.roombooking.booking.BookingRepository;
import com.example.roombooking.booking.BookingStatus;
import com.example.roombooking.error.BadRequestException;
import com.example.roombooking.room.Room;
import com.example.roombooking.room.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

@ExtendWith(MockitoExtension.class)
class FreeSlotServiceTest {

    private static final LocalDate DAY = LocalDate.of(2026, 1, 12);
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 1, 5, 8, 0);

    @Mock
    private BookingRepository bookingRepository;
    @Mock
    private RoomRepository roomRepository;

    private FreeSlotService service;
    private Room aspen;
    private Room birch;
    private Room coop;

    @BeforeEach
    void setUp() {
        service = new FreeSlotService(bookingRepository, roomRepository);
        aspen = room(1L, "Aspen", 8, true);
        birch = room(2L, "Birch", 8, true);
        coop = room(3L, "Coop", 1, true);
    }

    @Test
    void returnsWholeWorkingDayWhenNobodyIsBusy() {
        givenBookings(List.of());

        List<FreeSlotResponse> slots = service.findFreeSlots(DAY, 60, List.of("ann@example.com"));

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).start()).isEqualTo(DAY.atTime(9, 0));
        assertThat(slots.get(0).end()).isEqualTo(DAY.atTime(18, 0));
        assertThat(slots.get(0).rooms()).extracting(FreeSlotResponse.RoomSuggestion::name)
                .containsExactly("Aspen", "Birch", "Coop");
    }

    @Test
    void mergesOverlappingBookingsAndUnionsAllAttendees() {
        givenBookings(List.of(
                booking(aspen, "10:00", "11:00", "ann@example.com"),
                booking(birch, "09:30", "10:30", "ann@example.com"),
                booking(aspen, "13:00", "14:00", "bob@example.com"),
                booking(birch, "13:15", "13:45", "bob@example.com")));

        List<FreeSlotResponse> slots = service.findFreeSlots(DAY, 30,
                List.of("ann@example.com", "bob@example.com"));

        assertThat(slots).extracting(FreeSlotServiceTest::timeRange)
                .containsExactly("09:00-09:30", "11:00-13:00", "14:00-18:00");
    }

    @Test
    void dropsSlotsShorterThanTheRequestedDuration() {
        givenBookings(List.of(
                booking(aspen, "09:00", "10:00", "ann@example.com"),
                booking(birch, "10:15", "18:00", "ann@example.com")));

        assertThat(service.findFreeSlots(DAY, 30, List.of("ann@example.com"))).isEmpty();
        assertThat(service.findFreeSlots(DAY, 15, List.of("ann@example.com")))
                .extracting(FreeSlotServiceTest::timeRange)
                .containsExactly("10:00-10:15");
    }

    @Test
    void suggestsOnlyRoomsThatAreBigEnoughAndFree() {
        givenBookings(List.of(booking(aspen, "10:00", "11:00", "other@example.com")));

        List<FreeSlotResponse> slots = service.findFreeSlots(DAY, 540,
                List.of("ann@example.com", "bob@example.com"));

        assertThat(slots).hasSize(1);
        assertThat(slots.get(0).rooms()).extracting(FreeSlotResponse.RoomSuggestion::name)
                .containsExactly("Birch");
    }

    @Test
    void clampsBusyIntervalsToTheWorkingDay() {
        givenBookings(List.of(
                booking(aspen, "07:00", "10:00", "ann@example.com"),
                booking(birch, "17:00", "20:00", "ann@example.com")));

        List<FreeSlotResponse> slots = service.findFreeSlots(DAY, 30, List.of("ann@example.com"));

        assertThat(slots).extracting(FreeSlotServiceTest::timeRange)
                .containsExactly("10:00-17:00");
    }

    @Test
    void treatsBackToBackBookingsAsOneBusyBlock() {
        givenBookings(List.of(
                booking(aspen, "09:00", "10:00", "ann@example.com"),
                booking(birch, "10:00", "11:00", "ann@example.com")));

        List<FreeSlotResponse> slots = service.findFreeSlots(DAY, 30, List.of("ann@example.com"));

        assertThat(slots).extracting(FreeSlotServiceTest::timeRange)
                .containsExactly("11:00-18:00");
    }

    @Test
    void matchesAttendeeEmailsRegardlessOfCaseAndWhitespace() {
        givenBookings(List.of(booking(aspen, "10:00", "11:00", "ann@example.com")));

        List<FreeSlotResponse> slots = service.findFreeSlots(DAY, 30, List.of(" ANN@Example.com "));

        assertThat(slots).extracting(FreeSlotServiceTest::timeRange)
                .containsExactly("09:00-10:00", "11:00-18:00");
    }

    @Test
    void rejectsNonPositiveDuration() {
        assertThatThrownBy(() -> service.findFreeSlots(DAY, 0, List.of("ann@example.com")))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getFieldErrors())
                        .containsKey("durationMinutes"));
    }

    @Test
    void rejectsMissingAttendees() {
        assertThatThrownBy(() -> service.findFreeSlots(DAY, 30, List.of()))
                .isInstanceOf(BadRequestException.class)
                .satisfies(ex -> assertThat(((BadRequestException) ex).getFieldErrors())
                        .containsKey("attendees"));
    }

    private void givenBookings(List<Booking> bookings) {
        when(bookingRepository.findActiveBetween(BookingStatus.ACTIVE,
                DAY.atStartOfDay(), DAY.plusDays(1).atStartOfDay())).thenReturn(bookings);
        when(roomRepository.findByActiveTrue()).thenReturn(List.of(aspen, birch, coop));
    }

    private static String timeRange(FreeSlotResponse slot) {
        return slot.start().toLocalTime() + "-" + slot.end().toLocalTime();
    }

    private static Booking booking(Room room, String start, String end, String... attendees) {
        return new Booking(room, "Meeting", attendees[0], Set.of(attendees),
                DAY.atTime(LocalTime.parse(start)), DAY.atTime(LocalTime.parse(end)), NOW);
    }

    private static Room room(Long id, String name, int capacity, boolean active) {
        Room room = new Room(name, capacity, active);
        ReflectionTestUtils.setField(room, "id", id);
        return room;
    }
}
