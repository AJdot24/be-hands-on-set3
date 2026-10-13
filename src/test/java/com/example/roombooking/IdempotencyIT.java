package com.example.roombooking;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.example.roombooking.booking.BookingRepository;
import com.example.roombooking.booking.IdempotencyKeyRepository;
import com.example.roombooking.booking.dto.BookingResponse;
import com.example.roombooking.error.ApiError;
import com.example.roombooking.room.Room;
import com.example.roombooking.room.RoomRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "booking.seed-rooms=false",
        "spring.datasource.url=jdbc:h2:mem:idempotencyit;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000"
})
class IdempotencyIT {

    @Autowired
    private TestRestTemplate rest;

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Autowired
    private IdempotencyKeyRepository idempotencyKeyRepository;

    private Room room;
    private LocalDate day;

    @BeforeEach
    void setUp() {
        idempotencyKeyRepository.deleteAll();
        bookingRepository.deleteAll();
        roomRepository.deleteAll();
        room = roomRepository.save(new Room("Idem Room", 8, true));
        day = LocalDate.now().plusDays(7);
    }

    @Test
    void retryingTheSameRequestCreatesOnlyOneBooking() {
        ResponseEntity<BookingResponse> first = post(body(10, 11), "retry-key-1");
        assertThat(first.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<BookingResponse> retry = post(body(10, 11), "retry-key-1");
        assertThat(retry.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(retry.getBody().id()).isEqualTo(first.getBody().id());
        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    @Test
    void reusingAKeyWithADifferentPayloadIsRejected() {
        assertThat(post(body(10, 11), "retry-key-2").getStatusCode()).isEqualTo(HttpStatus.CREATED);

        ResponseEntity<ApiError> conflict = rest.exchange("/api/bookings", HttpMethod.POST,
                new HttpEntity<>(body(11, 12), jsonHeaders("retry-key-2")), ApiError.class);

        assertThat(conflict.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
        assertThat(conflict.getBody().message()).contains("different request payload");
        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    @Test
    void concurrentRetriesCreateOnlyOneBooking() throws Exception {
        int attempts = 4;
        ExecutorService pool = Executors.newFixedThreadPool(attempts);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<ResponseEntity<BookingResponse>>> futures = new ArrayList<>();
        for (int i = 0; i < attempts; i++) {
            futures.add(pool.submit(() -> {
                start.await();
                return post(body(10, 11), "concurrent-retry-key");
            }));
        }
        start.countDown();

        List<ResponseEntity<BookingResponse>> responses = new ArrayList<>();
        for (Future<ResponseEntity<BookingResponse>> future : futures) {
            responses.add(future.get(60, TimeUnit.SECONDS));
        }
        pool.shutdown();

        assertThat(responses).filteredOn(r -> r.getStatusCode() == HttpStatus.CREATED).hasSize(1);
        assertThat(responses).filteredOn(r -> r.getStatusCode() == HttpStatus.OK).hasSize(3);
        Set<Long> bookingIds = responses.stream().map(r -> r.getBody().id()).collect(Collectors.toSet());
        assertThat(bookingIds).hasSize(1);
        assertThat(bookingRepository.count()).isEqualTo(1);
    }

    private ResponseEntity<BookingResponse> post(Map<String, Object> body, String idempotencyKey) {
        return rest.exchange("/api/bookings", HttpMethod.POST,
                new HttpEntity<>(body, jsonHeaders(idempotencyKey)), BookingResponse.class);
    }

    private static HttpHeaders jsonHeaders(String idempotencyKey) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", idempotencyKey);
        return headers;
    }

    private Map<String, Object> body(int startHour, int endHour) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("roomId", room.getId());
        body.put("title", "Standup");
        body.put("organiserEmail", "ann@example.com");
        body.put("attendees", List.of("bob@example.com"));
        body.put("start", day.atTime(startHour, 0).toString());
        body.put("end", day.atTime(endHour, 0).toString());
        return body;
    }
}
