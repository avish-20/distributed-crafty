package com.avish.distributed_craftly.workspace_service.dto.project;

import jakarta.validation.constraints.NotBlank;

public record ProjectRequest(
       @NotBlank String name
) {
}
