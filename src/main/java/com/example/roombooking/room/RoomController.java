package com.example.roombooking.room;

import java.time.LocalDate;
import java.util.List;

import com.example.roombooking.booking.BookingService;
import com.example.roombooking.booking.dto.BookingResponse;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rooms")
public class RoomController {

    private final BookingService bookingService;

    public RoomController(BookingService bookingService) {
        this.bookingService = bookingService;
    }

    @GetMapping("/{roomId}/bookings")
    public List<BookingResponse> listBookings(
            @PathVariable Long roomId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return bookingService.listRoomBookings(roomId, date);
    }
}
