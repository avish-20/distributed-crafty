package com.avish.distributed_craftly.workspace_service.service;

import com.avish.distributed_craftly.workspace_service.dto.project.DeployResponse;

public interface DeploymentService {
    DeployResponse deploy(Long projectId);
}
