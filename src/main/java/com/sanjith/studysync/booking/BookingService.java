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
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DeadlockLoserDataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class BookingService {

    private static final int DEADLOCK_RETRY_ATTEMPTS = 5;
    private static final int DEADLOCK_RETRY_BACKOFF_MILLIS = 50;

    private final BookingRepository bookingRepository;
    private final RoomRepository roomRepository;
    private final GroupService groupService;
    private final Clock clock;
    private final TransactionTemplate transactionTemplate;

    public BookingService(
            BookingRepository bookingRepository,
            RoomRepository roomRepository,
            GroupService groupService,
            Clock clock,
            PlatformTransactionManager transactionManager) {
        this.bookingRepository = bookingRepository;
        this.roomRepository = roomRepository;
        this.groupService = groupService;
        this.clock = clock;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
        this.transactionTemplate.setIsolationLevel(TransactionDefinition.ISOLATION_DEFAULT);
    }

    public Booking createBooking(
            User user, Long roomId, Long groupId, LocalDateTime startTime, LocalDateTime endTime) {
        if (!startTime.isBefore(endTime)) {
            throw new InvalidBookingRequestException("Booking start time must be before end time");
        }
        if (!startTime.isAfter(LocalDateTime.now(clock))) {
            throw new InvalidBookingRequestException("Booking start time must be in the future");
        }

        // Concurrent identical inserts race for a ShareLock on the GiST exclusion index and can
        // deadlock (PostgreSQL SQLState 40P01). The DB exclusion constraint remains the authority
        // that decides which booking wins; a deadlock merely aborts one contender as a victim.
        // Instead of serializing all same-room bookings, we retry the deadlocked victim until it
        // either commits or the exclusion constraint rejects it as a conflict.
        for (int attempt = 1; attempt <= DEADLOCK_RETRY_ATTEMPTS; attempt++) {
            try {
                return transactionTemplate.execute(status -> doCreateBooking(user, roomId, groupId, startTime, endTime));
            } catch (RuntimeException ex) {
                if (!isDeadlock(ex)) {
                    throw ex;
                }
                if (attempt == DEADLOCK_RETRY_ATTEMPTS) {
                    throw new BookingConflictException("Room is already booked for the requested time range");
                }
                sleep(DEADLOCK_RETRY_BACKOFF_MILLIS * attempt);
            }
        }
        throw new IllegalStateException("Unreachable");
    }

    private boolean isDeadlock(RuntimeException ex) {
        Throwable t = ex;
        while (t != null) {
            if (t instanceof CannotAcquireLockException) {
                return true;
            }
            if (t instanceof DeadlockLoserDataAccessException) {
                return true;
            }
            // Hibernate may surface a 40P01 deadlock through many wrapper types. Fall back to the
            // underlying JDBC SQLState so retries fire regardless of how Spring translated it.
            if (t instanceof java.sql.SQLException) {
                String sqlState = ((java.sql.SQLException) t).getSQLState();
                if ("40P01".equals(sqlState)) {
                    return true;
                }
            }
            t = t.getCause();
        }
        return false;
    }

    private void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ie) {
            Thread.currentThread().interrupt();
        }
    }

    private Booking doCreateBooking(
            User user, Long roomId, Long groupId, LocalDateTime startTime, LocalDateTime endTime) {
        Room room = roomRepository.findByIdForUpdate(roomId)
                .orElseThrow(() -> new ResourceNotFoundException("Room not found: " + roomId));

        StudyGroup group = null;
        if (groupId != null) {
            group = groupService.findById(groupId);
            if (!groupService.isMember(groupId, user.getId())) {
                throw new AccessDeniedException("You must be a member of this group to book on its behalf");
            }
        }

        if (bookingRepository.existsOverlapping(roomId, startTime, endTime)) {
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
            // The exclusion constraint rejected a double-booking, so the requested time range is
            // genuinely taken. Reported as a 409 conflict.
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
