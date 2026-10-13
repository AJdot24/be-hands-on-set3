# Room Booking Service (be-hands-on-set3)

Meeting room booking REST API built with **Java 17**, **Spring Boot 3.5**, **Spring Data JPA** and an **H2 in-memory database**. Maven is the build tool (wrapper included).

## Questions & pull requests

| # | Question | PR |
|---|----------|----|
| 1 | Room Booking API | _to be linked_ |
| 2 | Free Slots & Safe Booking | _to be linked_ |

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

See the pull requests above for the endpoints added in each question.
