package com.example.roombooking.slots;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import com.example.roombooking.booking.Booking;
import com.example.roombooking.booking.BookingRepository;
import com.example.roombooking.booking.BookingStatus;
import com.example.roombooking.error.BadRequestException;
import com.example.roombooking.room.Room;
import com.example.roombooking.room.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FreeSlotService {

    static final LocalTime OPENING_TIME = LocalTime.of(9, 0);
    static final LocalTime CLOSING_TIME = LocalTime.of(18, 0);

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;

    public FreeSlotService(BookingRepository bookingRepository, RoomRepository roomRepository) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
    }

    @Transactional(readOnly = true)
    public List<FreeSlotResponse> findFreeSlots(LocalDate date, int durationMinutes, List<String> attendeeEmails) {
        Map<String, String> errors = new LinkedHashMap<>();
        if (durationMinutes < 1) {
            errors.put("durationMinutes", "must be at least 1");
        }
        Set<String> attendees = normaliseAttendees(attendeeEmails, errors);
        if (!errors.isEmpty()) {
            throw new BadRequestException(errors);
        }

        LocalDateTime dayStart = date.atTime(OPENING_TIME);
        LocalDateTime dayEnd = date.atTime(CLOSING_TIME);
        List<Booking> bookings = bookingRepository.findActiveBetween(
                BookingStatus.ACTIVE, date.atStartOfDay(), date.plusDays(1).atStartOfDay());

        Map<Long, List<Booking>> bookingsByRoom = bookings.stream()
                .collect(Collectors.groupingBy(booking -> booking.getRoom().getId()));
        List<Room> activeRooms = roomRepository.findByActiveTrue();

        return commonFreeIntervals(bookings, attendees, dayStart, dayEnd).stream()
                .filter(interval -> interval.minutes() >= durationMinutes)
                .map(interval -> new FreeSlotResponse(interval.start(), interval.end(),
                        availableRooms(activeRooms, bookingsByRoom, interval, attendees.size())))
                .toList();
    }

    private List<Interval> commonFreeIntervals(List<Booking> bookings, Set<String> attendees,
                                               LocalDateTime dayStart, LocalDateTime dayEnd) {
        List<Interval> busy = bookings.stream()
                .filter(booking -> !Collections.disjoint(booking.getAttendeeEmails(), attendees))
                .map(booking -> new Interval(
                        max(booking.getStartAt(), dayStart),
                        min(booking.getEndAt(), dayEnd)))
                .filter(interval -> interval.start().isBefore(interval.end()))
                .sorted(Comparator.comparing(Interval::start))
                .toList();
        return complement(merge(busy), dayStart, dayEnd);
    }

    private List<FreeSlotResponse.RoomSuggestion> availableRooms(List<Room> rooms,
                                                                 Map<Long, List<Booking>> bookingsByRoom,
                                                                 Interval slot, int groupSize) {
        return rooms.stream()
                .filter(room -> room.getCapacity() >= groupSize)
                .filter(room -> bookingsByRoom.getOrDefault(room.getId(), List.of()).stream()
                        .noneMatch(booking -> overlaps(booking, slot)))
                .map(room -> new FreeSlotResponse.RoomSuggestion(room.getId(), room.getName(), room.getCapacity()))
                .toList();
    }

    private static boolean overlaps(Booking booking, Interval slot) {
        return booking.getStartAt().isBefore(slot.end()) && booking.getEndAt().isAfter(slot.start());
    }

    private static List<Interval> merge(List<Interval> sorted) {
        List<Interval> merged = new ArrayList<>();
        for (Interval interval : sorted) {
            if (!merged.isEmpty() && !interval.start().isAfter(merged.get(merged.size() - 1).end())) {
                Interval last = merged.remove(merged.size() - 1);
                merged.add(new Interval(last.start(), max(last.end(), interval.end())));
            } else {
                merged.add(interval);
            }
        }
        return merged;
    }

    private static List<Interval> complement(List<Interval> busy, LocalDateTime dayStart, LocalDateTime dayEnd) {
        List<Interval> free = new ArrayList<>();
        LocalDateTime cursor = dayStart;
        for (Interval interval : busy) {
            if (interval.start().isAfter(cursor)) {
                free.add(new Interval(cursor, interval.start()));
            }
            cursor = max(cursor, interval.end());
        }
        if (cursor.isBefore(dayEnd)) {
            free.add(new Interval(cursor, dayEnd));
        }
        return free;
    }

    private static Set<String> normaliseAttendees(List<String> attendeeEmails, Map<String, String> errors) {
        Set<String> attendees = new LinkedHashSet<>();
        if (attendeeEmails == null || attendeeEmails.isEmpty()) {
            errors.put("attendees", "at least one attendee email is required");
            return attendees;
        }
        for (String email : attendeeEmails) {
            if (email == null || email.isBlank()) {
                errors.put("attendees", "attendee emails must not be blank");
                continue;
            }
            attendees.add(email.trim().toLowerCase(Locale.ROOT));
        }
        return attendees;
    }

    private static LocalDateTime max(LocalDateTime first, LocalDateTime second) {
        return first.isAfter(second) ? first : second;
    }

    private static LocalDateTime min(LocalDateTime first, LocalDateTime second) {
        return first.isBefore(second) ? first : second;
    }

    private record Interval(LocalDateTime start, LocalDateTime end) {

        long minutes() {
            return Duration.between(start, end).toMinutes();
        }
    }
}
