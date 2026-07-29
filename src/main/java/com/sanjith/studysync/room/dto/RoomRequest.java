package com.sanjith.studysync.room.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

public record RoomRequest(
        @NotBlank String name,
        @Min(1) int capacity,
        @NotBlank String location) {
}
