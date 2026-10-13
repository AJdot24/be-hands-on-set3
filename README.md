# Room Booking Service (be-hands-on-set3)

Meeting room booking REST API built with **Java 17**, **Spring Boot 3.5**, **Spring Data JPA** and an **H2 in-memory database**. Maven is the build tool (wrapper included).

## Questions & pull requests

| # | Question | PR |
|---|----------|----|
| 1 | Room Booking API | [#1](https://github.com/AJdot24/be-hands-on-set3/pull/1) |
| 2 | Free Slots & Safe Booking | [#2](https://github.com/AJdot24/be-hands-on-set3/pull/2) |

## How to run

Requires JDK 17+ (Maven wrapper included, no local Maven needed).

```shell
# Windows
.\mvnw.cmd spring-boot:run

# macOS / Linux
./mvnw spring-boot:run
```

The app starts on `http://localhost:8080`. Rooms are seeded on startup and the H2 console is available at `http://localhost:8080/h2-console` (JDBC URL `jdbc:h2:mem:bookingdb`).

## How to test

```shell
# Windows
.\mvnw.cmd test

# macOS / Linux
./mvnw test
```

## API overview

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/bookings` | Create a booking (optional `Idempotency-Key` header for safe retries) |
| GET | `/api/bookings/{id}` | Fetch one booking (includes status) |
| DELETE | `/api/bookings/{id}` | Cancel a booking (idempotent; cancelled bookings stop blocking the room) |
| GET | `/api/rooms/{roomId}/bookings?date=YYYY-MM-DD` | Non-cancelled bookings for a room on a date, in time order |
| GET | `/api/free-slots?date=&durationMinutes=&attendees=` | Common free slots for the attendees, with suitable free rooms per slot |

Rules: bookings are on a single day, 09:00-18:00, on 15-minute steps, at most 4 hours, not in the past, attendees (including the organiser) fit the room capacity, and do not overlap another booking in the same room (back-to-back is allowed). Invalid input returns `400` with a message per field, unknown room/booking `404`, overlap `409` naming the clashing booking; all errors share one JSON shape.

Concurrency and retries: booking creation takes a database row lock (`SELECT ... FOR UPDATE`) on the room, so simultaneous overlapping requests produce exactly one booking (the rest get `409`) - also when several app instances share the database. A retried request with the same `Idempotency-Key` returns the original booking instead of creating a duplicate; reusing a key with a different payload is rejected with `409`.

Example:

```shell
curl -X POST http://localhost:8080/api/bookings \
  -H "Content-Type: application/json" -H "Idempotency-Key: demo-1" \
  -d '{"roomId":1,"title":"Standup","organiserEmail":"ann@example.com","attendees":["bob@example.com"],"start":"2026-10-20T10:00:00","end":"2026-10-20T11:00:00"}'

curl "http://localhost:8080/api/free-slots?date=2026-10-20&durationMinutes=30&attendees=ann@example.com&attendees=bob@example.com"
```
