package com.sanjith.studysync.booking.dto;

import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;

public record BookingRequest(
        @NotNull Long roomId,
        Long groupId,
        @NotNull LocalDateTime startTime,
        @NotNull LocalDateTime endTime) {
}
