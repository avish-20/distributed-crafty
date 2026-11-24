package com.avish.distributed_craftly.workspace_service.dto.member;

import com.avish.distributed_craftly.common_lib.enums.ProjectRole;
import jakarta.validation.constraints.NotNull;

public record UpdateMemberRoleRequest(
        @NotNull ProjectRole role) {
}
