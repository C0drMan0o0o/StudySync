package com.sanjith.studysync.room.dto;

import com.sanjith.studysync.room.Room;

public record RoomResponse(Long id, String name, int capacity, String location) {

    public static RoomResponse from(Room room) {
        return new RoomResponse(room.getId(), room.getName(), room.getCapacity(), room.getLocation());
    }
}
