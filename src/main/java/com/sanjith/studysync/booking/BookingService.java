package com.sanjith.studysync.booking;

import com.sanjith.studysync.common.exception.BookingConflictException;
import com.sanjith.studysync.common.exception.InvalidBookingRequestException;
import com.sanjith.studysync.common.exception.ResourceNotFoundException;
import com.sanjith.studysync.group.GroupService;
import com.sanjith.studysync.group.StudyGroup;
import com.sanjith.studysync.room.Room;
import com.sanjith.studysync.room.RoomRepository;
import com.sanjith.studysync.user.User;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class BookingService {

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final GroupService groupService;
    private final Clock clock;

    public BookingService(
            BookingRepository bookingRepository,
            RoomRepository roomRepository,
            GroupService groupService,
            Clock clock) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.groupService = groupService;
        this.clock = clock;
    }

    @Transactional
    public Booking createBooking(
            User user, Long roomId, Long groupId, LocalDateTime startTime, LocalDateTime endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new InvalidBookingRequestException("Booking start time must be before end time");
        }
        if (!startTime.isAfter(LocalDateTime.now(clock))) {
            throw new InvalidBookingRequestException("Booking start time must be in the future");
        }

        Room room = roomRepository.findById(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + roomId));

        StudyGroup group = null;
        if (groupId != null) {
            group = groupService.findById(groupId);
            if (!groupService.isMember(groupId, user.getId())) {
                throw new AccessDeniedException("You must be a member of this group to book on its behalf");
            }
        }

        if (!bookingRepository.findOverlapping(roomId, startTime, endTime).isEmpty()) {
            throw new BookingConflictException("Room is already booked for the requested time range");
        }

        Booking booking = Booking.builder()
                .user(user)
                .room(room)
                .group(group)
                .startTime(startTime)
                .endTime(endTime)
                .createdAt(LocalDateTime.now(clock))
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
