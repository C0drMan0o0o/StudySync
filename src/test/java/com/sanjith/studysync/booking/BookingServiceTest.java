package com.sanjith.studysync.booking;

import com.sanjith.studysync.common.exception.BookingConflictException;
import com.sanjith.studysync.common.exception.ResourceNotFoundException;
import com.sanjith.studysync.room.Room;
import com.sanjith.studysync.room.RoomRepository;
import com.sanjith.studysync.user.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private RoomRepository roomRepository;

    private BookingService bookingService;

    private final Room room = Room.builder().id(1L).name("Room A").capacity(4).location("Floor 1").build();
    private final User user = User.builder().id(1L).email("alice@example.com").name("Alice").build();
    private final LocalDateTime start = LocalDateTime.now().plusDays(1);
    private final LocalDateTime end = start.plusHours(1);

    @Test
    void createBookingSucceedsWhenNoOverlap() {
        bookingService = new BookingService(bookingRepository, roomRepository);
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(bookingRepository.findOverlapping(1L, start, end)).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.createBooking(user, 1L, start, end);

        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getRoom()).isEqualTo(room);
        assertThat(result.getStartTime()).isEqualTo(start);
        assertThat(result.getEndTime()).isEqualTo(end);
    }

    @Test
    void createBookingThrowsConflictWhenOverlapExists() {
        bookingService = new BookingService(bookingRepository, roomRepository);
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(bookingRepository.findOverlapping(1L, start, end))
                .thenReturn(List.of(Booking.builder().id(2L).build()));

        assertThatThrownBy(() -> bookingService.createBooking(user, 1L, start, end))
                .isInstanceOf(BookingConflictException.class);
    }

    @Test
    void createBookingThrowsWhenStartIsNotBeforeEnd() {
        bookingService = new BookingService(bookingRepository, roomRepository);

        assertThatThrownBy(() -> bookingService.createBooking(user, 1L, end, start))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createBookingThrowsWhenStartIsInThePast() {
        bookingService = new BookingService(bookingRepository, roomRepository);
        LocalDateTime pastStart = LocalDateTime.now().minusDays(1);

        assertThatThrownBy(() -> bookingService.createBooking(user, 1L, pastStart, pastStart.plusHours(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void createBookingThrowsWhenRoomDoesNotExist() {
        bookingService = new BookingService(bookingRepository, roomRepository);
        when(roomRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(user, 99L, start, end))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createBookingTranslatesDataIntegrityViolationToBookingConflict() {
        bookingService = new BookingService(bookingRepository, roomRepository);
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(bookingRepository.findOverlapping(1L, start, end)).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenThrow(new DataIntegrityViolationException("conflict"));

        assertThatThrownBy(() -> bookingService.createBooking(user, 1L, start, end))
                .isInstanceOf(BookingConflictException.class);
    }

    @Test
    void cancelBookingRemovesBookingWhenOwnedByRequestingUser() {
        bookingService = new BookingService(bookingRepository, roomRepository);
        Booking booking = Booking.builder().id(1L).user(user).room(room).build();
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        bookingService.cancelBooking(1L, 1L);
    }

    @Test
    void cancelBookingThrowsWhenRequestingUserIsNotOwner() {
        bookingService = new BookingService(bookingRepository, roomRepository);
        Booking booking = Booking.builder().id(1L).user(user).room(room).build();
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(1L, 2L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancelBookingThrowsWhenBookingDoesNotExist() {
        bookingService = new BookingService(bookingRepository, roomRepository);
        when(bookingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.cancelBooking(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
