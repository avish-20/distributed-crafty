package com.avish.distributed_craftly.workspace_service.dto.project;

import com.avish.distributed_craftly.common_lib.enums.ProjectRole;

import java.time.Instant;

public record ProjectSummaryResponse(
        ProjectRole role,
        Long id,
        String name,
        Instant createdAt,
        Instant updatedAt
) {
}
