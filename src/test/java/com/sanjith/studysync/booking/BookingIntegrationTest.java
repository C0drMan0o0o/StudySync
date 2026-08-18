package com.sanjith.studysync.booking;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sanjith.studysync.BaseIntegrationTest;
import com.sanjith.studysync.booking.dto.BookingRequest;
import com.sanjith.studysync.group.GroupRepository;
import com.sanjith.studysync.group.StudyGroup;
import com.sanjith.studysync.room.Room;
import com.sanjith.studysync.room.RoomRepository;
import com.sanjith.studysync.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Transactional
class BookingIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RoomRepository roomRepository;

    @Autowired
    private GroupRepository groupRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @Test
    void bookRoomSucceedsWithValidFutureSlot() throws Exception {
        String token = createAndLoginUser("booker@example.com", "Booker User");
        User user = userRepository.findByEmail("booker@example.com").orElseThrow();

        Room room = roomRepository.save(Room.builder()
                .name("Conference Room A")
                .capacity(8)
                .location("Floor 1")
                .build());

        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(10).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);

        BookingRequest request = new BookingRequest(room.getId(), null, start, end);

        mockMvc.perform(post("/bookings")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.roomId").value(room.getId()))
                .andExpect(jsonPath("$.userId").value(user.getId()))
                .andExpect(jsonPath("$.groupId").isEmpty());
    }

    @Test
    void bookRoomForGroupVerifiesMembership() throws Exception {
        String memberToken = createAndLoginUser("groupmember@example.com", "Member User");
        User member = userRepository.findByEmail("groupmember@example.com").orElseThrow();

        String nonMemberToken = createAndLoginUser("nonmember@example.com", "Non Member User");
        User nonMember = userRepository.findByEmail("nonmember@example.com").orElseThrow();

        Room room = roomRepository.save(Room.builder()
                .name("Group Room B")
                .capacity(12)
                .location("Floor 2")
                .build());

        // Creator creates study group and binds member as the first member
        StudyGroup group = StudyGroup.builder()
                .name("Math Group")
                .createdBy(member)
                .createdAt(LocalDateTime.now())
                .build();
        group.getMembers().add(member);
        group = groupRepository.save(group);

        LocalDateTime start = LocalDateTime.now().plusDays(2).withHour(14).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(2);

        // 1. Group member books room for group -> Expect 201 Created
        BookingRequest memberRequest = new BookingRequest(room.getId(), group.getId(), start, end);
        mockMvc.perform(post("/bookings")
                        .header("Authorization", memberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(memberRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.groupId").value(group.getId()));

        // 2. Non-group member tries to book room for group -> Expect 403 Forbidden
        LocalDateTime otherStart = start.plusDays(1);
        LocalDateTime otherEnd = otherStart.plusHours(2);
        BookingRequest nonMemberRequest = new BookingRequest(room.getId(), group.getId(), otherStart, otherEnd);
        mockMvc.perform(post("/bookings")
                        .header("Authorization", nonMemberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(nonMemberRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void bookRoomRejectsOverlappingSlots() throws Exception {
        String token = createAndLoginUser("overlap@example.com", "Overlap User");

        Room room = roomRepository.save(Room.builder()
                .name("Overlap Room")
                .capacity(6)
                .location("Floor 3")
                .build());

        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(12).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(2); // 12:00 - 14:00

        BookingRequest firstRequest = new BookingRequest(room.getId(), null, start, end);
        mockMvc.perform(post("/bookings")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(firstRequest)))
                .andExpect(status().isCreated());

        // 1. Overlapping start (13:00 - 15:00) -> Expect 409 Conflict
        BookingRequest secondRequest = new BookingRequest(room.getId(), null, start.plusHours(1), end.plusHours(1));
        mockMvc.perform(post("/bookings")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(secondRequest)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Room is already booked for the requested time range"));
    }

    @Test
    void getMyBookingsReturnsUserBookings() throws Exception {
        String user1Token = createAndLoginUser("user1@example.com", "User 1");
        User user1 = userRepository.findByEmail("user1@example.com").orElseThrow();

        String user2Token = createAndLoginUser("user2@example.com", "User 2");

        Room room = roomRepository.save(Room.builder()
                .name("Common Room")
                .capacity(20)
                .location("Floor 1")
                .build());

        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(9).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);

        // User 1 books 9:00 - 10:00
        mockMvc.perform(post("/bookings")
                        .header("Authorization", user1Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BookingRequest(room.getId(), null, start, end))))
                .andExpect(status().isCreated());

        // User 2 books 10:00 - 11:00
        mockMvc.perform(post("/bookings")
                        .header("Authorization", user2Token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BookingRequest(room.getId(), null, start.plusHours(1), end.plusHours(1)))))
                .andExpect(status().isCreated());

        // Query user 1's bookings -> Expect 1 booking belonging to user1
        mockMvc.perform(get("/bookings/me")
                        .header("Authorization", user1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].userId").value(user1.getId()));
    }

    @Test
    void cancelBookingSucceedsForOwnerAndFailsForOthers() throws Exception {
        String ownerToken = createAndLoginUser("owner@example.com", "Owner");
        User owner = userRepository.findByEmail("owner@example.com").orElseThrow();

        String otherToken = createAndLoginUser("otheruser@example.com", "Other User");

        Room room = roomRepository.save(Room.builder()
                .name("Cancel Room")
                .capacity(4)
                .location("Floor 4")
                .build());

        LocalDateTime start = LocalDateTime.now().plusDays(1).withHour(16).withMinute(0).withSecond(0).withNano(0);
        LocalDateTime end = start.plusHours(1);

        // Owner books room
        String bookingJson = mockMvc.perform(post("/bookings")
                        .header("Authorization", ownerToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new BookingRequest(room.getId(), null, start, end))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long bookingId = objectMapper.readTree(bookingJson).get("id").asLong();

        // 1. Other user tries to cancel -> Expect 403 Forbidden
        mockMvc.perform(delete("/bookings/" + bookingId)
                        .header("Authorization", otherToken))
                .andExpect(status().isForbidden());

        // 2. Owner cancels booking -> Expect 244 No Content
        mockMvc.perform(delete("/bookings/" + bookingId)
                        .header("Authorization", ownerToken))
                .andExpect(status().isNoContent());

        // 3. Confirm deletion
        mockMvc.perform(get("/bookings/me")
                        .header("Authorization", ownerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }
}
