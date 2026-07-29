package com.sanjith.studysync.room;

import com.sanjith.studysync.common.exception.ResourceNotFoundException;
import com.sanjith.studysync.room.dto.RoomRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoomService {

    private final RoomRepository roomRepository;

    public RoomService(RoomRepository roomRepository) {
        this.roomRepository = roomRepository;
    }

    public List<Room> findAll() {
        return roomRepository.findAll();
    }

    public Room findById(Long id) {
        return roomRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + id));
    }

    public Room create(RoomRequest request) {
        Room room = Room.builder()
                .name(request.name())
                .capacity(request.capacity())
                .location(request.location())
                .build();
        return roomRepository.save(room);
    }

    public Room update(Long id, RoomRequest request) {
        Room room = findById(id);
        room.setName(request.name());
        room.setCapacity(request.capacity());
        room.setLocation(request.location());
        return roomRepository.save(room);
    }

    public void delete(Long id) {
        Room room = findById(id);
        roomRepository.delete(room);
    }
}
