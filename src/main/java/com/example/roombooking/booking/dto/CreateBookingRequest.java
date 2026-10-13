package com.example.roombooking.booking.dto;

import java.time.LocalDateTime;
import java.util.List;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateBookingRequest(
        @NotNull(message = "roomId is required") Long roomId,
        @NotBlank(message = "title is required") String title,
        @NotBlank(message = "organiserEmail is required")
        @Email(message = "must be a valid email") String organiserEmail,
        List<@NotBlank(message = "attendee email must not be blank")
             @Email(message = "must be a valid email") String> attendees,
        @NotNull(message = "start is required") LocalDateTime start,
        @NotNull(message = "end is required") LocalDateTime end) {
}
