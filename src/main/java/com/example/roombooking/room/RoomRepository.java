package com.example.roombooking.room;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RoomRepository extends JpaRepository<Room, Long> {

    Optional<Room> findByName(String name);

    List<Room> findByActiveTrue();
}
