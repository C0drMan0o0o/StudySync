package com.sanjith.studysync.group.dto;

import com.sanjith.studysync.group.StudyGroup;
import com.sanjith.studysync.user.User;
import java.util.List;

public record GroupResponse(Long id, String name, Long creatorId, List<Long> memberIds) {

    public static GroupResponse from(StudyGroup group) {
        return new GroupResponse(
                group.getId(),
                group.getName(),
                group.getCreatedBy().getId(),
                group.getMembers().stream().map(User::getId).toList());
    }
}
