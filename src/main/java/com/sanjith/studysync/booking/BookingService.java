package com.sanjith.studysync.booking;

import com.sanjith.studysync.common.exception.BookingConflictException;
import com.sanjith.studysync.common.exception.ResourceNotFoundException;
import com.sanjith.studysync.room.Room;
import com.sanjith.studysync.room.RoomRepository;
import com.sanjith.studysync.user.User;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;

    public BookingService(BookingRepository bookingRepository, RoomRepository roomRepository) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
    }

    public Booking createBooking(User user, Long roomId, LocalDateTime startTime, LocalDateTime endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new IllegalArgumentException("Booking start time must be before end time");
        }
        if (!startTime.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("Booking start time must be in the future");
        }

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + roomId));

        if (!bookingRepository.findOverlapping(roomId, startTime, endTime).isEmpty()) {
            throw new BookingConflictException("Room is already booked for the requested time range");
        }

        Booking booking = Booking.builder()
                .user(user)
                .room(room)
                .startTime(startTime)
                .endTime(endTime)
                .createdAt(LocalDateTime.now())
                .build();

        try {
            return bookingRepository.save(booking);
        } catch (DataIntegrityViolationException ex) {
            throw new BookingConflictException("Room is already booked for the requested time range");
        }
    }

    public List<Booking> findByUser(Long userId) {
        return bookingRepository.findByUserId(userId);
    }

    public List<Booking> findByRoom(Long roomId) {
        return bookingRepository.findByRoomId(roomId);
    }

    public void cancelBooking(Long bookingId, Long requestingUserId) {
        Booking booking = bookingRepository.findById(bookingId)
                .orElseThrow(() -> new ResourceNotFoundException("Booking not found: " + bookingId));

        if (!booking.getUser().getId().equals(requestingUserId)) {
            throw new AccessDeniedException("You do not have permission to cancel this booking");
        }

        bookingRepository.delete(booking);
    }
}
