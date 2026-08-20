package com.sanjith.studysync.group;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.sanjith.studysync.common.exception.LastGroupMemberException;
import com.sanjith.studysync.common.exception.ResourceNotFoundException;
import com.sanjith.studysync.group.dto.GroupRequest;
import com.sanjith.studysync.user.User;
import com.sanjith.studysync.user.UserRepository;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

@ExtendWith(MockitoExtension.class)
class GroupServiceTest {

    @Mock
    private GroupRepository groupRepository;

    @Mock
    private UserRepository userRepository;

    private GroupService groupService;

    private final User creator = User.builder().id(1L).email("alice@example.com").name("Alice").build();
    private final User member = User.builder().id(2L).email("bob@example.com").name("Bob").build();
    private final Clock clock = Clock.fixed(Instant.parse("2024-01-01T00:00:00Z"), ZoneOffset.UTC);

    @Test
    void createGroupAddsCreatorAsFirstMemberAndOwner() {
        groupService = new GroupService(groupRepository, userRepository, clock);
        when(groupRepository.save(any(StudyGroup.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudyGroup result = groupService.createGroup(creator, new GroupRequest("CS Study Group"));

        assertThat(result.getCreatedBy()).isEqualTo(creator);
        assertThat(result.getMembers()).containsExactly(creator);
    }

    @Test
    void addMemberSucceedsWhenRequesterIsAMember() {
        groupService = new GroupService(groupRepository, userRepository, clock);
        StudyGroup group = groupWithMembers(creator);
        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        when(groupRepository.existsByIdAndMembersId(1L, creator.getId())).thenReturn(true);
        when(userRepository.findById(2L)).thenReturn(Optional.of(member));
        when(groupRepository.save(any(StudyGroup.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudyGroup result = groupService.addMember(1L, 2L, creator.getId());

        assertThat(result.getMembers()).contains(creator, member);
    }

    @Test
    void addMemberThrowsWhenRequesterIsNotAMember() {
        groupService = new GroupService(groupRepository, userRepository, clock);
        StudyGroup group = groupWithMembers(creator);
        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> groupService.addMember(1L, 2L, member.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void addMemberThrowsWhenGroupDoesNotExist() {
        groupService = new GroupService(groupRepository, userRepository, clock);
        when(groupRepository.findByIdForUpdate(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> groupService.addMember(99L, 2L, creator.getId()))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    void removeMemberAllowsSelfRemovalWhenOtherMembersRemain() {
        groupService = new GroupService(groupRepository, userRepository, clock);
        StudyGroup group = groupWithMembers(creator, member);
        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        when(groupRepository.existsByIdAndMembersId(1L, member.getId())).thenReturn(true);
        when(groupRepository.save(any(StudyGroup.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudyGroup result = groupService.removeMember(1L, member.getId(), member.getId());

        assertThat(result.getMembers()).containsExactly(creator);
    }

    @Test
    void removeMemberThrowsWhenNonCreatorTriesToRemoveSomeoneElse() {
        groupService = new GroupService(groupRepository, userRepository, clock);
        StudyGroup group = groupWithMembers(creator, member);
        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        when(groupRepository.existsByIdAndMembersId(1L, member.getId())).thenReturn(true);

        assertThatThrownBy(() -> groupService.removeMember(1L, creator.getId(), member.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void removeMemberAllowsCreatorToRemoveSomeoneElse() {
        groupService = new GroupService(groupRepository, userRepository, clock);
        StudyGroup group = groupWithMembers(creator, member);
        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        when(groupRepository.existsByIdAndMembersId(1L, creator.getId())).thenReturn(true);
        when(groupRepository.save(any(StudyGroup.class))).thenAnswer(invocation -> invocation.getArgument(0));

        StudyGroup result = groupService.removeMember(1L, member.getId(), creator.getId());

        assertThat(result.getMembers()).containsExactly(creator);
    }

    @Test
    void removeMemberThrowsWhenCreatorTriesToLeaveWhileOthersRemain() {
        groupService = new GroupService(groupRepository, userRepository, clock);
        StudyGroup group = groupWithMembers(creator, member);
        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        when(groupRepository.existsByIdAndMembersId(1L, creator.getId())).thenReturn(true);

        assertThatThrownBy(() -> groupService.removeMember(1L, creator.getId(), creator.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void removeMemberThrowsWhenRemovingTheLastMember() {
        groupService = new GroupService(groupRepository, userRepository, clock);
        StudyGroup group = groupWithMembers(creator);
        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));
        when(groupRepository.existsByIdAndMembersId(1L, creator.getId())).thenReturn(true);

        assertThatThrownBy(() -> groupService.removeMember(1L, creator.getId(), creator.getId()))
                .isInstanceOf(LastGroupMemberException.class);
    }

    @Test
    void removeMemberThrowsWhenRequesterIsNotAMember() {
        groupService = new GroupService(groupRepository, userRepository, clock);
        StudyGroup group = groupWithMembers(creator);
        when(groupRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(group));

        assertThatThrownBy(() -> groupService.removeMember(1L, creator.getId(), member.getId()))
                .isInstanceOf(AccessDeniedException.class);
    }

    private StudyGroup groupWithMembers(User... members) {
        StudyGroup group = StudyGroup.builder().id(1L).name("CS Study Group").createdBy(creator).build();
        for (User groupMember : members) {
            group.getMembers().add(groupMember);
        }
        return group;
    }
}
