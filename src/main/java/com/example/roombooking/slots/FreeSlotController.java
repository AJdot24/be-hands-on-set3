package com.example.roombooking.slots;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/free-slots")
public class FreeSlotController {

    private final FreeSlotService freeSlotService;

    public FreeSlotController(FreeSlotService freeSlotService) {
        this.freeSlotService = freeSlotService;
    }

    @GetMapping
    public List<FreeSlotResponse> findFreeSlots(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam int durationMinutes,
            @RequestParam List<String> attendees) {
        return freeSlotService.findFreeSlots(date, durationMinutes, attendees);
    }
}
