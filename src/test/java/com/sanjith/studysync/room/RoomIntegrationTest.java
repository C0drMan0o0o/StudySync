package com.sanjith.studysync.room;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sanjith.studysync.BaseIntegrationTest;
import com.sanjith.studysync.room.dto.RoomRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class RoomIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private RoomRepository roomRepository;

    @Test
    void unauthenticatedRequestReturns403() throws Exception {
        mockMvc.perform(get("/rooms"))
                .andExpect(status().isForbidden());
    }

    @Test
    void roomCrudFlowSucceeds() throws Exception {
        String token = createAndLoginUser("admin@example.com");

        // 1. Create room
        RoomRequest createReq = new RoomRequest("Study Room A", 10, "Library 2nd Floor");
        String createJson = mockMvc.perform(post("/rooms")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Study Room A"))
                .andExpect(jsonPath("$.capacity").value(10))
                .andExpect(jsonPath("$.location").value("Library 2nd Floor"))
                .andReturn().getResponse().getContentAsString();

        Long roomId = objectMapper.readTree(createJson).get("id").asLong();

        // 2. Get room by ID
        mockMvc.perform(get("/rooms/" + roomId)
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Study Room A"));

        // 3. Get all rooms
        mockMvc.perform(get("/rooms")
                        .header("Authorization", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Study Room A"));

        // 4. Update room
        RoomRequest updateReq = new RoomRequest("Study Room A Updated", 15, "Library 3rd Floor");
        mockMvc.perform(put("/rooms/" + roomId)
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Study Room A Updated"))
                .andExpect(jsonPath("$.capacity").value(15))
                .andExpect(jsonPath("$.location").value("Library 3rd Floor"));

        // 5. Delete room
        mockMvc.perform(delete("/rooms/" + roomId)
                        .header("Authorization", token))
                .andExpect(status().isNoContent());

        // 6. Verify 404
        mockMvc.perform(get("/rooms/" + roomId)
                        .header("Authorization", token))
                .andExpect(status().isNotFound());
    }

    @Test
    void createRoomFailsWithValidationErrors() throws Exception {
        String token = createAndLoginUser("admin@example.com");

        RoomRequest invalidReq = new RoomRequest("", 0, "");

        mockMvc.perform(post("/rooms")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalidReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").isNotEmpty())
                .andExpect(jsonPath("$.errors.capacity").isNotEmpty())
                .andExpect(jsonPath("$.errors.location").isNotEmpty());
    }
}
