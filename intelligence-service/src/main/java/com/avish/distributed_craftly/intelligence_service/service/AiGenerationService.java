package com.avish.distributed_craftly.intelligence_service.service;

import com.avish.distributed_craftly.intelligence_service.dto.chat.StreamResponse;
import reactor.core.publisher.Flux;

public interface AiGenerationService {
    Flux<StreamResponse> streamResponse(String message, Long projectId);
}
