package com.example.roombooking.booking;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import com.example.roombooking.booking.dto.BookingResponse;
import com.example.roombooking.booking.dto.CreateBookingRequest;
import com.example.roombooking.error.BadRequestException;
import com.example.roombooking.error.NotFoundException;
import com.example.roombooking.room.Room;
import com.example.roombooking.room.RoomRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    static final LocalTime OPENING_TIME = LocalTime.of(9, 0);
    static final LocalTime CLOSING_TIME = LocalTime.of(18, 0);
    static final Duration MAX_DURATION = Duration.ofHours(4);
    static final int MINUTES_STEP = 15;

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final Clock clock;

    public BookingService(BookingRepository bookingRepository, RoomRepository roomRepository, Clock clock) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.clock = clock;
    }

    @Transactional
    public BookingResponse createBooking(CreateBookingRequest request) {
        Room room = roomRepository.findById(request.roomId())
                .orElseThrow(() -> new NotFoundException("Room " + request.roomId() + " not found"));

        Set<String> attendees = normalizeAttendees(request.organiserEmail(), request.attendees());
        Map<String, String> fieldErrors = validate(request, room, attendees);
        if (!fieldErrors.isEmpty()) {
            throw new BadRequestException(fieldErrors);
        }

        Booking booking = new Booking(room, request.title().trim(),
                normalizeEmail(request.organiserEmail()), attendees,
                request.start(), request.end(), LocalDateTime.now(clock));
        return BookingResponse.from(bookingRepository.save(booking));
    }

    @Transactional(readOnly = true)
    public BookingResponse getBooking(Long id) {
        Booking booking = bookingRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("Booking " + id + " not found"));
        return BookingResponse.from(booking);
    }

    private Map<String, String> validate(CreateBookingRequest request, Room room, Set<String> attendees) {
        Map<String, String> errors = new LinkedHashMap<>();
        LocalDateTime start = request.start();
        LocalDateTime end = request.end();

        if (!start.isBefore(end)) {
            addError(errors, "end", "must be after start");
        }
        if (!start.toLocalDate().equals(end.toLocalDate())) {
            addError(errors, "start", "booking must start and end on the same day");
        }
        if (start.toLocalTime().isBefore(OPENING_TIME)) {
            addError(errors, "start", "must be at or after 09:00");
        }
        if (end.toLocalTime().isAfter(CLOSING_TIME)) {
            addError(errors, "end", "must be at or before 18:00");
        }
        if (!isOnStep(start)) {
            addError(errors, "start", "must be on a 15-minute step");
        }
        if (!isOnStep(end)) {
            addError(errors, "end", "must be on a 15-minute step");
        }
        if (start.isBefore(end) && Duration.between(start, end).compareTo(MAX_DURATION) > 0) {
            addError(errors, "end", "must be at most 4 hours after start");
        }
        if (start.isBefore(LocalDateTime.now(clock))) {
            addError(errors, "start", "must not be in the past");
        }
        if (attendees.size() > room.getCapacity()) {
            addError(errors, "attendees", "number of attendees (" + attendees.size()
                    + ") exceeds room capacity (" + room.getCapacity() + ")");
        }
        return errors;
    }

    private static boolean isOnStep(LocalDateTime time) {
        return time.getMinute() % MINUTES_STEP == 0 && time.getSecond() == 0 && time.getNano() == 0;
    }

    private static void addError(Map<String, String> errors, String field, String message) {
        errors.merge(field, message, (existing, added) -> existing + "; " + added);
    }

    private static Set<String> normalizeAttendees(String organiserEmail, List<String> attendees) {
        Set<String> normalized = new LinkedHashSet<>();
        normalized.add(normalizeEmail(organiserEmail));
        if (attendees != null) {
            attendees.forEach(email -> normalized.add(normalizeEmail(email)));
        }
        return normalized;
    }

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }
}
