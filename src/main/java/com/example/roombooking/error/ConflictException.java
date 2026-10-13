package com.example.roombooking.error;

public class ConflictException extends RuntimeException {

    private final Long conflictingBookingId;

    public ConflictException(String message, Long conflictingBookingId) {
        super(message);
        this.conflictingBookingId = conflictingBookingId;
    }

    public Long getConflictingBookingId() {
        return conflictingBookingId;
    }
}
