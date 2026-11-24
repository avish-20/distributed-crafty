package com.avish.distributed_craftly.intelligence_service.dto.chat;


import com.avish.distributed_craftly.common_lib.enums.MessageRole;

import java.time.Instant;
import java.util.List;

public record ChatResponse(
        Long id,


        MessageRole role,
        List<ChatEventResponse> events,
        String content,// NULL unless USER role
        Integer tokensUsed,

        Instant createdAt) {
}
