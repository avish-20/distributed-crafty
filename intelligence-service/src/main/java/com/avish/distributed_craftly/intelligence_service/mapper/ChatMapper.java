package com.avish.distributed_craftly.intelligence_service.mapper;


import com.avish.distributed_craftly.intelligence_service.dto.chat.ChatResponse;
import com.avish.distributed_craftly.intelligence_service.entity.ChatMessage;
import org.mapstruct.Mapper;

import java.util.List;

@Mapper(componentModel = "spring")
public interface ChatMapper {
    List<ChatResponse> toListOfChatResponse(List<ChatMessage> messages);
}
