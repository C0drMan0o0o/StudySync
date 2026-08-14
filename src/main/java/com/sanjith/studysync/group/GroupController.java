package com.sanjith.studysync.group;

import com.sanjith.studysync.common.exception.ResourceNotFoundException;
import com.sanjith.studysync.group.dto.AddMemberRequest;
import com.sanjith.studysync.group.dto.GroupRequest;
import com.sanjith.studysync.group.dto.GroupResponse;
import com.sanjith.studysync.user.User;
import com.sanjith.studysync.user.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/groups")
public class GroupController {

    private final GroupService groupService;
    private final UserRepository userRepository;

    public GroupController(GroupService groupService, UserRepository userRepository) {
        this.groupService = groupService;
        this.userRepository = userRepository;
    }

    @Operation(summary = "Create a study group, with the creator as its first member")
    @ApiResponse(responseCode = "201", description = "Group created")
    @PostMapping
    public ResponseEntity<GroupResponse> create(
            @Valid @RequestBody GroupRequest request, Authentication authentication) {
        User user = currentUser(authentication);
        StudyGroup group = groupService.createGroup(user, request);
        return ResponseEntity.status(HttpStatus.CREATED).body(GroupResponse.from(group));
    }

    @Operation(summary = "List the current user's study groups")
    @GetMapping("/me")
    public List<GroupResponse> getMyGroups(Authentication authentication) {
        User user = currentUser(authentication);
        return groupService.findByMember(user.getId()).stream().map(GroupResponse::from).toList();
    }

    @Operation(summary = "Add a member to a group (requester must already be a member)")
    @ApiResponse(responseCode = "403", description = "Requester is not a member of this group")
    @ApiResponse(responseCode = "404", description = "Group or user not found")
    @PostMapping("/{id}/members")
    public GroupResponse addMember(
            @PathVariable Long id, @Valid @RequestBody AddMemberRequest request, Authentication authentication) {
        User user = currentUser(authentication);
        StudyGroup group = groupService.addMember(id, request.userId(), user.getId());
        return GroupResponse.from(group);
    }

    @Operation(summary = "Remove a member from a group (self-removal is always allowed; "
            + "removing someone else requires being the group creator)")
    @ApiResponse(responseCode = "403", description = "Requester is not a member, or is not the group creator "
            + "and tried to remove someone else")
    @ApiResponse(responseCode = "404", description = "Group not found")
    @ApiResponse(responseCode = "409", description = "Cannot remove the last remaining member of a group")
    @DeleteMapping("/{id}/members/{userId}")
    public GroupResponse removeMember(
            @PathVariable Long id, @PathVariable Long userId, Authentication authentication) {
        User user = currentUser(authentication);
        StudyGroup group = groupService.removeMember(id, userId, user.getId());
        return GroupResponse.from(group);
    }

    private User currentUser(Authentication authentication) {
        String email = authentication.getName();
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + email));
    }
}
