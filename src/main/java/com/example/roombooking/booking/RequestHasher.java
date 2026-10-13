package com.example.roombooking.booking;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Locale;

import com.example.roombooking.booking.dto.CreateBookingRequest;

final class RequestHasher {

    private RequestHasher() {
    }

    /**
     * Canonical fingerprint of a booking request. Attendee order and email case
     * do not change the hash, so a retry with an equivalent payload is treated
     * as the same request.
     */
    static String hash(CreateBookingRequest request) {
        StringBuilder canonical = new StringBuilder()
                .append(request.roomId()).append('|')
                .append(request.title().trim()).append('|')
                .append(request.organiserEmail().trim().toLowerCase(Locale.ROOT)).append('|')
                .append(request.start()).append('|')
                .append(request.end()).append('|');
        if (request.attendees() != null) {
            request.attendees().stream()
                    .map(email -> email.trim().toLowerCase(Locale.ROOT))
                    .sorted()
                    .forEach(email -> canonical.append(email).append(','));
        }
        return sha256Hex(canonical.toString());
    }

    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder(hash.length * 2);
            for (byte b : hash) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }
            return hex.toString();
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 not available", ex);
        }
    }
}
