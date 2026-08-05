package com.sanjith.studysync.booking;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface BookingRepository extends JpaRepository<Booking, Long> {

    @Query("SELECT b FROM Booking b WHERE b.room.id = :roomId "
            + "AND b.startTime < :end AND b.endTime > :start")
    List<Booking> findOverlapping(
            @Param("roomId") Long roomId, @Param("start") LocalDateTime start, @Param("end") LocalDateTime end);

    List<Booking> findByUserId(Long userId);

    List<Booking> findByRoomId(Long roomId);
}
