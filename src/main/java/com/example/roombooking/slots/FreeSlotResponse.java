package com.example.roombooking.slots;

import java.time.LocalDateTime;
import java.util.List;

public record FreeSlotResponse(LocalDateTime start, LocalDateTime end, List<RoomSuggestion> rooms) {

    public record RoomSuggestion(Long id, String name, int capacity) {
    }
}
