package com.sanjith.studysync.group;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.sanjith.studysync.BaseIntegrationTest;
import com.sanjith.studysync.group.dto.AddMemberRequest;
import com.sanjith.studysync.group.dto.GroupRequest;
import com.sanjith.studysync.user.User;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.transaction.annotation.Transactional;

@Transactional
class GroupIntegrationTest extends BaseIntegrationTest {

    @Autowired
    private GroupRepository groupRepository;

    @Test
    void createGroupSucceedsWithCreatorAsFirstMember() throws Exception {
        String token = createAndLoginUser("creator@example.com", "Creator User");
        User creator = userRepository.findByEmail("creator@example.com").orElseThrow();

        GroupRequest request = new GroupRequest("Java Study Group");

        mockMvc.perform(post("/groups")
                        .header("Authorization", token)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Java Study Group"))
                .andExpect(jsonPath("$.creatorId").value(creator.getId()))
                .andExpect(jsonPath("$.memberIds[0]").value(creator.getId()));
    }

    @Test
    void addMemberSucceedsIfRequesterIsMember() throws Exception {
        String creatorToken = createAndLoginUser("creator2@example.com", "Creator User 2");
        User creator = userRepository.findByEmail("creator2@example.com").orElseThrow();

        // Create a group
        GroupRequest createReq = new GroupRequest("Spring Study Group");
        String groupJson = mockMvc.perform(post("/groups")
                        .header("Authorization", creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long groupId = objectMapper.readTree(groupJson).get("id").asLong();

        // Create a new user to add
        createAndLoginUser("member@example.com", "Member User");
        User member = userRepository.findByEmail("member@example.com").orElseThrow();

        // Add member using creator's token
        AddMemberRequest addReq = new AddMemberRequest(member.getId());
        mockMvc.perform(post("/groups/" + groupId + "/members")
                        .header("Authorization", creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberIds").value(org.hamcrest.Matchers.hasItems(creator.getId().intValue(), member.getId().intValue())));
    }

    @Test
    void addMemberFailsIfRequesterIsNotMember() throws Exception {
        String creatorToken = createAndLoginUser("creator3@example.com", "Creator User 3");
        User creator = userRepository.findByEmail("creator3@example.com").orElseThrow();

        // Create a group
        GroupRequest createReq = new GroupRequest("Kotlin Study Group");
        String groupJson = mockMvc.perform(post("/groups")
                        .header("Authorization", creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long groupId = objectMapper.readTree(groupJson).get("id").asLong();

        // Create a non-member user
        String nonMemberToken = createAndLoginUser("nonmember@example.com", "Non Member");
        User nonMember = userRepository.findByEmail("nonmember@example.com").orElseThrow();

        // Create another user to add
        createAndLoginUser("other@example.com", "Other User");
        User other = userRepository.findByEmail("other@example.com").orElseThrow();

        // Try to add other user using non-member's token
        AddMemberRequest addReq = new AddMemberRequest(other.getId());
        mockMvc.perform(post("/groups/" + groupId + "/members")
                        .header("Authorization", nonMemberToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(addReq)))
                .andExpect(status().isForbidden());
    }

    @Test
    void removeMemberSelfRemovalAndCreatorRemovalAndBlocks() throws Exception {
        String creatorToken = createAndLoginUser("creator4@example.com", "Creator User 4");
        User creator = userRepository.findByEmail("creator4@example.com").orElseThrow();

        // Create a group
        GroupRequest createReq = new GroupRequest("Docker Study Group");
        String groupJson = mockMvc.perform(post("/groups")
                        .header("Authorization", creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long groupId = objectMapper.readTree(groupJson).get("id").asLong();

        // Create member 1
        String member1Token = createAndLoginUser("member1@example.com", "Member 1");
        User member1 = userRepository.findByEmail("member1@example.com").orElseThrow();

        // Create member 2
        String member2Token = createAndLoginUser("member2@example.com", "Member 2");
        User member2 = userRepository.findByEmail("member2@example.com").orElseThrow();

        // Add member 1 and member 2 to the group
        mockMvc.perform(post("/groups/" + groupId + "/members")
                        .header("Authorization", creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddMemberRequest(member1.getId()))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/groups/" + groupId + "/members")
                        .header("Authorization", creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new AddMemberRequest(member2.getId()))))
                .andExpect(status().isOk());

        // 1. Non-creator member (member1) tries to remove someone else (member2) -> Expect 403 Forbidden
        mockMvc.perform(delete("/groups/" + groupId + "/members/" + member2.getId())
                        .header("Authorization", member1Token))
                .andExpect(status().isForbidden());

        // 2. Creator tries to self-remove while other members remain -> Expect 403 Forbidden
        mockMvc.perform(delete("/groups/" + groupId + "/members/" + creator.getId())
                        .header("Authorization", creatorToken))
                .andExpect(status().isForbidden());

        // 3. Member self-removes -> Expect 200 OK
        mockMvc.perform(delete("/groups/" + groupId + "/members/" + member1.getId())
                        .header("Authorization", member1Token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberIds").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(member1.getId().intValue()))));

        // 4. Creator removes remaining member (member2) -> Expect 200 OK
        mockMvc.perform(delete("/groups/" + groupId + "/members/" + member2.getId())
                        .header("Authorization", creatorToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.memberIds").value(org.hamcrest.Matchers.not(org.hamcrest.Matchers.hasItem(member2.getId().intValue()))));
    }

    @Test
    void removingLastMemberThrowsConflict() throws Exception {
        String creatorToken = createAndLoginUser("creator5@example.com", "Creator User 5");
        User creator = userRepository.findByEmail("creator5@example.com").orElseThrow();

        // Create a group
        GroupRequest createReq = new GroupRequest("Git Study Group");
        String groupJson = mockMvc.perform(post("/groups")
                        .header("Authorization", creatorToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createReq)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        Long groupId = objectMapper.readTree(groupJson).get("id").asLong();

        // Remove the creator (who is the last member) -> Expect 409 Conflict
        mockMvc.perform(delete("/groups/" + groupId + "/members/" + creator.getId())
                        .header("Authorization", creatorToken))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Cannot remove the last member of a group"));
    }
}
