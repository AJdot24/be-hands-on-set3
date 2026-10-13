package com.example.roombooking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.example.roombooking.booking.BookingRepository;
import com.example.roombooking.room.Room;
import com.example.roombooking.room.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "booking.seed-rooms=false",
        "spring.datasource.url=jdbc:h2:mem:concurrentit;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
})
class ConcurrentBookingIT {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    private Room room;
    private LocalDate day;

    @BeforeEach
    void setUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();
        room = roomRepository.save(new Room("Contended Room", 8, true));
        day = LocalDate.now().plusDays(7);
    }

    @Test
    @Timeout(60)
    void exactlyOneOfTwentySimultaneousOverlappingBookingsSucceeds() throws Exception {
        int attempts = 20;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch ready = new CountDownLatch(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<String>>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            Map<String, Object> request = body();
            futures.add(pool.submit(() -> {
                ready.countDown();
                start.await();
                return rest.postForEntity("/api/bookings", request, String.class);
            }));
        }
        assertThat(ready.await(30, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<ResponseEntity<String>> responses = new ArrayList<>();
        for (Future<ResponseEntity<String>> future : futures) {
            responses.add(future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(responses).filteredOn(r -> r.getStatusCode() == HttpStatus.CREATED).hasSize(1);
        assertThat(responses).filteredOn(r -> r.getStatusCode() == HttpStatus.CONFLICT).hasSize(19);
        assertThat(responses).filteredOn(r -> r.getStatusCode() == HttpStatus.CONFLICT)
                .allSatisfy(r -> assertThat(r.getBody()).contains("already booked"));
        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    private Map<String, Object> body() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roomId", room.getId());
        body.put("title", "Contended meeting");
        body.put("organiserEmail", "ann@example.com");
        body.put("attendees", List.of("ann@example.com"));
        body.put("start", day.atTime(10, 0).toString());
        body.put("end", day.atTime(11, 0).toString());
        return body;
    }
}
