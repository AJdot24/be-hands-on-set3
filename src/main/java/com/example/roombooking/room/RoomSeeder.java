package com.example.roombooking.room;

import java.util.List;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "booking.seed-rooms", havingValue = "true", matchIfMissing = true)
public class RoomSeeder implements ApplicationRunner {

    private final RoomRepository roomRepository;

    public RoomSeeder(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        List.of(
                new Room("Aspen", 4, true),
                new Room("Birch", 8, true),
                new Room("Cedar", 12, true),
                new Room("Dune", 6, true),
                new Room("Elm", 20, true))
                .forEach(room -> {
                    if (roomRepository.findByName(room.getName()).isEmpty()) {
                        roomRepository.save(room);
                    }
                });
    }
}
