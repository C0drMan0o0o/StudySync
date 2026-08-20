package com.sanjith.studysync.group;

import com.sanjith.studysync.common.exception.LastGroupMemberException;
import com.sanjith.studysync.common.exception.ResourceNotFoundException;
import com.sanjith.studysync.group.dto.GroupRequest;
import com.sanjith.studysync.user.User;
import com.sanjith.studysync.user.UserRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class GroupService {

    private final GroupRepository groupRepository;
    private final UserRepository userRepository;
    private final Clock clock;

    public GroupService(GroupRepository groupRepository, UserRepository userRepository, Clock clock) {
        this.groupRepository = groupRepository;
        this.userRepository = userRepository;
        this.clock = clock;
    }

    public StudyGroup createGroup(User creator, GroupRequest request) {
        StudyGroup group = StudyGroup.builder()
                .name(request.name())
                .createdAt(LocalDateTime.now(clock))
                .createdBy(creator)
                .build();
        group.getMembers().add(creator);
        return groupRepository.save(group);
    }

    public List<StudyGroup> findByMember(Long userId) {
        return groupRepository.findByMembersId(userId);
    }

    public StudyGroup findById(Long groupId) {
        return groupRepository.findById(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));
    }

    public boolean isMember(Long groupId, Long userId) {
        return groupRepository.existsByIdAndMembersId(groupId, userId);
    }

    @Transactional
    public StudyGroup addMember(Long groupId, Long userId, Long requestingUserId) {
        StudyGroup group = findByIdForUpdate(groupId);
        requireMember(group, requestingUserId);

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResourceNotFoundException("User not found: " + userId));

        group.getMembers().add(user);
        return groupRepository.save(group);
    }

    @Transactional
    public StudyGroup removeMember(Long groupId, Long userId, Long requestingUserId) {
        StudyGroup group = findByIdForUpdate(groupId);
        requireMember(group, requestingUserId);

        boolean isSelfRemoval = requestingUserId.equals(userId);
        boolean requesterIsCreator = group.getCreatedBy().getId().equals(requestingUserId);
        if (!isSelfRemoval && !requesterIsCreator) {
            throw new AccessDeniedException("Only the group creator can remove other members");
        }
        if (isSelfRemoval && requesterIsCreator && group.getMembers().size() > 1) {
            throw new AccessDeniedException("Group creator cannot leave while other members remain");
        }

        boolean targetIsMember = group.getMembers().stream().anyMatch(member -> member.getId().equals(userId));
        if (targetIsMember && group.getMembers().size() == 1) {
            throw new LastGroupMemberException("Cannot remove the last member of a group");
        }

        group.getMembers().removeIf(member -> member.getId().equals(userId));
        return groupRepository.save(group);
    }

    private StudyGroup findByIdForUpdate(Long groupId) {
        return groupRepository.findByIdForUpdate(groupId)
                .orElseThrow(() -> new ResourceNotFoundException("Group not found: " + groupId));
    }

    private void requireMember(StudyGroup group, Long userId) {
        if (!isMember(group.getId(), userId)) {
            throw new AccessDeniedException("You must be a member of this group to manage it");
        }
    }
}
