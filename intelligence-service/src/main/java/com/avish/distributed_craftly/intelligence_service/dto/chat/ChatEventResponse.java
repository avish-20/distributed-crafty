package com.avish.distributed_craftly.intelligence_service.dto.chat;


import com.avish.distributed_craftly.common_lib.enums.ChatEventType;

public record ChatEventResponse(Long id, ChatEventType type, Integer sequenceOrder,
                                String content, String filePath, // NULL unless FILE_EDIT
                                String metadata) {
}
