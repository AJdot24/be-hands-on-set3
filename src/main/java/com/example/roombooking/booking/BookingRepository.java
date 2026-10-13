package com.example.roombooking.booking;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("""
            select b from Booking b
            where b.room.id = :roomId
              and b.status = :status
              and b.startAt < :end
              and b.endAt > :start
            order by b.startAt
            """)
    List<Booking> findOverlapping(@Param("roomId") Long roomId,
                                  @Param("status") BookingStatus status,
                                  @Param("start") LocalDateTime start,
                                  @Param("end") LocalDateTime end);
}
