package com.sanjith.studysync.booking;

import com.sanjith.studysync.booking.dto.BookingRequest;
import com.sanjith.studysync.booking.dto.BookingResponse;
import com.sanjith.studysync.common.exception.ResourceNotFoundException;
import com.sanjith.studysync.user.User;
import com.sanjith.studysync.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import com.sanjith.studysync.security.UserPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class BookingController {

    private final BookingService bookingService;
    private final UserRepository userRepository;

    public BookingController(BookingService bookingService, UserRepository userRepository) {
        this.bookingService = bookingService;
        this.userRepository = userRepository;
    }

    @Operation(summary = "Book a room, optionally on behalf of a study group")
    @ApiResponse(responseCode = "201", description = "Booking created")
    @ApiResponse(responseCode = "409", description = "Room already booked for the requested time range")
    @PostMapping("/bookings")
    public ResponseEntity<BookingResponse> create(
            @Valid @RequestBody BookingRequest request, Authentication authentication) {
        User user = currentUser(authentication);
        Booking booking = bookingService.createBooking(
                user, request.roomId(), request.groupId(), request.startTime(), request.endTime());
        return ResponseEntity.status(HttpStatus.CREATED).body(BookingResponse.from(booking));
    }

    @Operation(summary = "List the current user's bookings")
    @GetMapping("/bookings/me")
    public List<BookingResponse> getMyBookings(Authentication authentication) {
        User user = currentUser(authentication);
        return bookingService.findByUser(user.getId()).stream().map(BookingResponse::from).toList();
    }

    @Operation(summary = "List all bookings for a room")
    @GetMapping("/rooms/{id}/bookings")
    public List<BookingResponse> getRoomBookings(@PathVariable Long id) {
        return bookingService.findByRoom(id).stream().map(BookingResponse::from).toList();
    }

    @Operation(summary = "Cancel a booking you own")
    @ApiResponse(responseCode = "204", description = "Booking cancelled")
    @ApiResponse(responseCode = "403", description = "Not the owner of this booking")
    @ApiResponse(responseCode = "404", description = "Booking not found")
    @DeleteMapping("/bookings/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id, Authentication authentication) {
        User user = currentUser(authentication);
        bookingService.cancelBooking(id, user.getId());
        return ResponseEntity.noContent().build();
    }

    private User currentUser(Authentication authentication) {
        if (authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getUser();
        }
        throw new IllegalStateException("Unexpected principal type in SecurityContext");
    }
}
