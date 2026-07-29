package com.sanjith.studysync.room;

import com.sanjith.studysync.common.exception.ResourceNotFoundException;
import com.sanjith.studysync.room.dto.RoomRequest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RoomServiceTest {

    @Mock
    private RoomRepository roomRepository;

    private RoomService roomService;

    @Test
    void findAllReturnsAllRooms() {
        roomService = new RoomService(roomRepository);
        Room room = Room.builder().id(1L).name("Room A").capacity(4).location("Floor 1").build();
        when(roomRepository.findAll()).thenReturn(List.of(room));

        List<Room> result = roomService.findAll();

        assertThat(result).containsExactly(room);
    }

    @Test
    void findByIdReturnsRoomWhenPresent() {
        roomService = new RoomService(roomRepository);
        Room room = Room.builder().id(1L).name("Room A").capacity(4).location("Floor 1").build();
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));

        Room result = roomService.findById(1L);

        assertThat(result).isEqualTo(room);
    }

    @Test
    void findByIdThrowsWhenAbsent() {
        roomService = new RoomService(roomRepository);
        when(roomRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.findById(99L))
                .isInstanceOf(ResourceNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void createSavesNewRoomFromRequest() {
        roomService = new RoomService(roomRepository);
        RoomRequest request = new RoomRequest("Room A", 4, "Floor 1");
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Room result = roomService.create(request);

        assertThat(result.getName()).isEqualTo("Room A");
        assertThat(result.getCapacity()).isEqualTo(4);
        assertThat(result.getLocation()).isEqualTo("Floor 1");
    }

    @Test
    void updateModifiesExistingRoomAndSaves() {
        roomService = new RoomService(roomRepository);
        Room existing = Room.builder().id(1L).name("Old").capacity(2).location("Old Floor").build();
        when(roomRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(roomRepository.save(any(Room.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Room result = roomService.update(1L, new RoomRequest("New", 6, "New Floor"));

        assertThat(result.getName()).isEqualTo("New");
        assertThat(result.getCapacity()).isEqualTo(6);
        assertThat(result.getLocation()).isEqualTo("New Floor");
    }

    @Test
    void updateThrowsWhenRoomDoesNotExist() {
        roomService = new RoomService(roomRepository);
        when(roomRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.update(99L, new RoomRequest("New", 6, "New Floor")))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void deleteRemovesExistingRoom() {
        roomService = new RoomService(roomRepository);
        Room existing = Room.builder().id(1L).name("Room A").capacity(4).location("Floor 1").build();
        when(roomRepository.findById(1L)).thenReturn(Optional.of(existing));

        roomService.delete(1L);

        ArgumentCaptor<Room> captor = ArgumentCaptor.forClass(Room.class);
        verify(roomRepository).delete(captor.capture());
        assertThat(captor.getValue().getId()).isEqualTo(1L);
    }

    @Test
    void deleteThrowsWhenRoomDoesNotExist() {
        roomService = new RoomService(roomRepository);
        when(roomRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> roomService.delete(99L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
