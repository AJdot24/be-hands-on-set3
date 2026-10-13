package com.example.roombooking.booking.dto;

import java.time.LocalDateTime;
import java.util.List;

import com.example.roombooking.booking.Booking;

public record BookingResponse(Long id, Long roomId, String roomName, String title, String organiserEmail,
                              List<String> attendees, LocalDateTime start, LocalDateTime end,
                              LocalDateTime createdAt, String status) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getRoom().getId(),
                booking.getRoom().getName(),
                booking.getTitle(),
                booking.getOrganiserEmail(),
                List.copyOf(booking.getAttendeeEmails()),
                booking.getStartAt(),
                booking.getEndAt(),
                booking.getCreatedAt(),
                booking.getStatus().name());
    }
}
