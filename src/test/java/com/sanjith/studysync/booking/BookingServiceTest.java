package com.sanjith.studysync.booking;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sanjith.studysync.common.exception.BookingConflictException;
import com.sanjith.studysync.common.exception.InvalidBookingRequestException;
import com.sanjith.studysync.common.exception.ResourceNotFoundException;
import com.sanjith.studysync.group.GroupService;
import com.sanjith.studysync.room.Room;
import com.sanjith.studysync.room.RoomRepository;
import com.sanjith.studysync.user.User;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;

@ExtendWith(MockitoExtension.class)
class BookingServiceTest {

    @Mock
    private BookingRepository bookingRepository;

    @Mock
    private RoomRepository roomRepository;

    @Mock
    private GroupService groupService;

    @Mock
    private PlatformTransactionManager transactionManager;

    private BookingService bookingService;

    private final Room room = Room.builder().id(1L).name("Room A").capacity(4).location("Floor 1").build();
    private final User user = User.builder().id(1L).email("alice@example.com").name("Alice").build();
    private final Clock clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);
    private final LocalDateTime start = LocalDateTime.now(clock).plusDays(1);
    private final LocalDateTime end = start.plusHours(1);

    private void setUp() {
        bookingService = new BookingService(bookingRepository, roomRepository, groupService, clock, transactionManager);
    }

    @Test
    void createBookingSucceedsWhenNoOverlap() {
        setUp();
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(bookingRepository.findOverlapping(1L, start, end)).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Booking result = bookingService.createBooking(user, 1L, null, start, end);

        assertThat(result.getUser()).isEqualTo(user);
        assertThat(result.getRoom()).isEqualTo(room);
        assertThat(result.getGroup()).isNull();
        assertThat(result.getStartTime()).isEqualTo(start);
        assertThat(result.getEndTime()).isEqualTo(end);
    }

    @Test
    void createBookingThrowsConflictWhenOverlapExists() {
        setUp();
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(bookingRepository.findOverlapping(1L, start, end))
                .thenReturn(List.of(Booking.builder().id(2L).build()));

        assertThatThrownBy(() -> bookingService.createBooking(user, 1L, null, start, end))
                .isInstanceOf(BookingConflictException.class);
    }

    @Test
    void createBookingThrowsWhenStartIsNotBeforeEnd() {
        setUp();

        assertThatThrownBy(() -> bookingService.createBooking(user, 1L, null, end, start))
                .isInstanceOf(InvalidBookingRequestException.class);
    }

    @Test
    void createBookingThrowsWhenStartIsInThePast() {
        setUp();
        LocalDateTime pastStart = LocalDateTime.now(clock).minusDays(1);

        assertThatThrownBy(() -> bookingService.createBooking(user, 1L, null, pastStart, pastStart.plusHours(1)))
                .isInstanceOf(InvalidBookingRequestException.class);
    }

    @Test
    void createBookingThrowsWhenRoomDoesNotExist() {
        setUp();
        when(roomRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.createBooking(user, 99L, null, start, end))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void createBookingTranslatesDataIntegrityViolationToBookingConflict() {
        setUp();
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(bookingRepository.findOverlapping(1L, start, end)).thenReturn(Collections.emptyList());
        when(bookingRepository.save(any(Booking.class))).thenThrow(new DataIntegrityViolationException("conflict"));

        assertThatThrownBy(() -> bookingService.createBooking(user, 1L, null, start, end))
                .isInstanceOf(BookingConflictException.class);
    }

    @Test
    void createBookingThrowsWhenRequesterIsNotAGroupMember() {
        setUp();
        when(roomRepository.findById(1L)).thenReturn(Optional.of(room));
        when(groupService.findById(5L)).thenReturn(null);
        when(groupService.isMember(5L, user.getId())).thenReturn(false);

        assertThatThrownBy(() -> bookingService.createBooking(user, 1L, 5L, start, end))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancelBookingRemovesBookingWhenOwnedByRequestingUser() {
        setUp();
        Booking booking = Booking.builder().id(1L).user(user).room(room).build();
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        bookingService.cancelBooking(1L, 1L);
    }

    @Test
    void cancelBookingThrowsWhenRequestingUserIsNotOwner() {
        setUp();
        Booking booking = Booking.builder().id(1L).user(user).room(room).build();
        when(bookingRepository.findById(1L)).thenReturn(Optional.of(booking));

        assertThatThrownBy(() -> bookingService.cancelBooking(1L, 2L))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void cancelBookingThrowsWhenBookingDoesNotExist() {
        setUp();
        when(bookingRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> bookingService.cancelBooking(99L, 1L))
                .isInstanceOf(ResourceNotFoundException.class);
    }
}
