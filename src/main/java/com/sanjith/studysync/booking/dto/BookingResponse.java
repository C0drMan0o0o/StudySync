package com.sanjith.studysync.booking.dto;

import com.sanjith.studysync.booking.Booking;
import java.time.LocalDateTime;

public record BookingResponse(
        Long id, Long roomId, Long userId, LocalDateTime startTime, LocalDateTime endTime) {

    public static BookingResponse from(Booking booking) {
        return new BookingResponse(
                booking.getId(),
                booking.getRoom().getId(),
                booking.getUser().getId(),
                booking.getStartTime(),
                booking.getEndTime());
    }
}
