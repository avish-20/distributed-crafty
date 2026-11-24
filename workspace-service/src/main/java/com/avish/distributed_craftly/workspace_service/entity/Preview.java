package com.avish.distributed_craftly.workspace_service.entity;


import com.avish.distributed_craftly.common_lib.enums.PreviewStatus;

import java.time.Instant;

public class Preview {

    Long id;

    Project project;

    String namespace;//seperation of resources
    String podName;
    String previewUrl;

    PreviewStatus status;

    Instant startedAt;
    Instant terminatedAt;

    Instant createdAt;
}
