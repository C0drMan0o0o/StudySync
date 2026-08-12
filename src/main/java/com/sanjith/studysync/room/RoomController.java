package com.sanjith.studysync.room;

import com.sanjith.studysync.room.dto.RoomRequest;
import com.sanjith.studysync.room.dto.RoomResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/rooms")
public class RoomController {

    private final RoomService roomService;

    public RoomController(RoomService roomService) {
        this.roomService = roomService;
    }

    @Operation(summary = "List all rooms")
    @GetMapping
    public List<RoomResponse> getAll() {
        return roomService.findAll().stream().map(RoomResponse::from).toList();
    }

    @Operation(summary = "Get a room by id")
    @ApiResponse(responseCode = "404", description = "Room not found")
    @GetMapping("/{id}")
    public RoomResponse getById(@PathVariable Long id) {
        return RoomResponse.from(roomService.findById(id));
    }

    @Operation(summary = "Create a room")
    @ApiResponse(responseCode = "201", description = "Room created")
    @PostMapping
    public ResponseEntity<RoomResponse> create(@Valid @RequestBody RoomRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(RoomResponse.from(roomService.create(request)));
    }

    @Operation(summary = "Update a room")
    @ApiResponse(responseCode = "404", description = "Room not found")
    @PutMapping("/{id}")
    public RoomResponse update(@PathVariable Long id, @Valid @RequestBody RoomRequest request) {
        return RoomResponse.from(roomService.update(id, request));
    }

    @Operation(summary = "Delete a room")
    @ApiResponse(responseCode = "204", description = "Room deleted")
    @ApiResponse(responseCode = "404", description = "Room not found")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        roomService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
