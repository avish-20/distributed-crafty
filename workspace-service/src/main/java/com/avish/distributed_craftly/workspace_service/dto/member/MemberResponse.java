package com.avish.distributed_craftly.workspace_service.dto.member;


import com.avish.distributed_craftly.common_lib.enums.ProjectRole;

import java.time.Instant;

public record MemberResponse(
        Long userId,
        String username,
        String name,
        ProjectRole projectRole,
        Instant invitedAt
) {
}
