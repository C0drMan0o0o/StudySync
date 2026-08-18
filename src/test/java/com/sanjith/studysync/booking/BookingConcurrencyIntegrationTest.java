package com.sanjith.studysync.booking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.sanjith.studysync.BaseIntegrationTest;
import com.sanjith.studysync.booking.dto.BookingRequest;
import com.sanjith.studysync.room.Room;
import com.sanjith.studysync.room.RoomRepository;
import com.sanjith.studysync.user.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class BookingConcurrencyIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @AfterEach
    void cleanUp() {
        bookingRepository.deleteAll();
        roomRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    void concurrentBookingsSerializeExactlyOneSucceeds() throws Exception {
        // Create 10 users and generate JWT tokens
        int threadCount = 10;
        List<String> tokens = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            tokens.add(createAndLoginUser("concurrent" + i + "@example.com", "Concurrent User " + i));
        }

        // Create a shared room
        Room room = roomRepository.save(Room.builder()
                .name("Concurrency Room")
                .capacity(10)
                .location("Floor 5")
                .build());

        // Booking details for the same overlapping time slot
        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);
        BookingRequest request = new BookingRequest(room.getId(), null, start, end);
        String requestJson = objectMapper.writeValueAsString(request);

        // Prepare threads execution
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch finishLatch = new CountDownLatch(threadCount);

        List<Future<Integer>> futures = new ArrayList<>();
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger conflictCount = new AtomicInteger(0);
        AtomicInteger otherCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final String token = tokens.get(i);
            futures.add(executor.submit(() -> {
                startLatch.await(); // wait for start signal
                try {
                    MvcResult result = mockMvc.perform(post("/bookings")
                                    .header("Authorization", token)
                                    .contentType(MediaType.APPLICATION_JSON)
                                    .content(requestJson))
                            .andReturn();

                    int status = result.getResponse().getStatus();
                    if (status == 201) {
                        successCount.incrementAndGet();
                    } else if (status == 409) {
                        conflictCount.incrementAndGet();
                    } else {
                        otherCount.incrementAndGet();
                    }
                    return status;
                } finally {
                    finishLatch.countDown();
                }
            }));
        }

        // Release all threads simultaneously
        startLatch.countDown();
        finishLatch.await();
        executor.shutdown();

        // Assertions: Exactly 1 succeeds, all others fail with 409 Conflict
        assertThat(successCount.get()).isEqualTo(1);
        assertThat(conflictCount.get()).isEqualTo(threadCount - 1);
        assertThat(otherCount.get()).isEqualTo(0);

        // Verify that exactly 1 booking exists in the database
        List<Booking> bookings = bookingRepository.findByRoomId(room.getId());
        assertThat(bookings).hasSize(1);
    }
}
