package com.sanjith.studysync.booking;

import com.sanjith.studysync.booking.dto.BookingRequest;
import com.sanjith.studysync.booking.dto.BookingResponse;
import com.sanjith.studysync.common.exception.ResourceNotFoundException;
import com.sanjith.studysync.user.User;
import com.sanjith.studysync.user.UserRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class BookingController {

    private final BookingService bookingService;
    private final UserRepository userRepository;

    public BookingController(BookingService bookingService, UserRepository userRepository) {
        this.bookingService = bookingService;
        this.userRepository = userRepository;
    }

    @PostMapping("/bookings")
    public ResponseEntity<BookingResponse> create(
            @Valid @RequestBody BookingRequest request, Authentication authentication) {
        User user = currentUser(authentication);
        Booking booking = bookingService.createBooking(user, request.roomId(), request.startTime(), request.endTime());
        return ResponseEntity.status(HttpStatus.CREATED).body(BookingResponse.from(booking));
    }

    @GetMapping("/bookings/me")
    public List<BookingResponse> getMyBookings(Authentication authentication) {
        User user = currentUser(authentication);
        return bookingService.findByUser(user.getId()).stream().map(BookingResponse::from).toList();
    }

    @GetMapping("/rooms/{id}/bookings")
    public List<BookingResponse> getRoomBookings(@PathVariable Long id) {
        return bookingService.findByRoom(id).stream().map(BookingResponse::from).toList();
    }

    @DeleteMapping("/bookings/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id, Authentication authentication) {
        User user = currentUser(authentication);
        bookingService.cancelBooking(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    private User currentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
