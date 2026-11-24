package com.avish.distributed_craftly.intelligence_service.service.impl;

import com.avish.distributed_craftly.common_lib.enums.ChatEventStatus;
import com.avish.distributed_craftly.common_lib.enums.ChatEventType;
import com.avish.distributed_craftly.common_lib.enums.MessageRole;
import com.avish.distributed_craftly.common_lib.event.FileStoreRequestEvent;
import com.avish.distributed_craftly.common_lib.security.AuthUtil;
import com.avish.distributed_craftly.intelligence_service.client.WorkspaceClient;
import com.avish.distributed_craftly.intelligence_service.dto.chat.StreamResponse;
import com.avish.distributed_craftly.intelligence_service.entity.ChatEvent;
import com.avish.distributed_craftly.intelligence_service.entity.ChatMessage;
import com.avish.distributed_craftly.intelligence_service.entity.ChatSession;
import com.avish.distributed_craftly.intelligence_service.entity.ChatSessionId;
import com.avish.distributed_craftly.intelligence_service.llm.LlmResponseParser;
import com.avish.distributed_craftly.intelligence_service.llm.PromptUtils;
import com.avish.distributed_craftly.intelligence_service.llm.advisors.FileTreeContextAdvisor;
import com.avish.distributed_craftly.intelligence_service.llm.tools.CodeGenerationTools;
import com.avish.distributed_craftly.intelligence_service.repository.ChatEventRepository;
import com.avish.distributed_craftly.intelligence_service.repository.ChatMessageRepository;
import com.avish.distributed_craftly.intelligence_service.repository.ChatSessionRepository;
import com.avish.distributed_craftly.intelligence_service.service.AiGenerationService;
import com.avish.distributed_craftly.intelligence_service.service.UsageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
@Slf4j
public class AiGenerationServiceImpl implements AiGenerationService {
    private final ChatClient chatClient;
    private final AuthUtil authUtil;
    private final FileTreeContextAdvisor fileTreeContextAdvisor;
    private final LlmResponseParser llmResponseParser;
    private final ChatSessionRepository chatSessionRepository;
    //private static final Pattern FILE_TAG_PATTERN = Pattern.compile("<file path=\"([^\"]+)\">(.*?)</file>", Pattern.DOTALL);
    private final ChatMessageRepository chatMessageRepository;
    private final ChatEventRepository chatEventRepository;
    private final UsageService usageService;
    private final WorkspaceClient workspaceClient;
    private final KafkaTemplate<String,Object> kafkaTemplate;


    @Override
    @PreAuthorize("@security.canEditProject(#projectId)")
    public Flux<StreamResponse> streamResponse(String userMessage, Long projectId) {
        // get current authenticated user id
        //usageService.checkDailyTokensUsage();
        Long userId = authUtil.getCurrentUserId();

        // ensure there is a chat session for this project and user
        ChatSession chatSession = createChatSessionIfNotExist(projectId, userId);

        CodeGenerationTools codeGenerationTools = new CodeGenerationTools(workspaceClient, projectId);


        Map<String, Object> advisorParams = Map
                .of("projectId", projectId, "userId", userId);

        StringBuilder fullResponseBuffer = new StringBuilder();
        log.debug("streamResponse called for projectId={} userId={} message={}", projectId, userId, userMessage);

        AtomicReference<Long> startTime = new AtomicReference<>(System.currentTimeMillis());
        AtomicReference<Long> endTime = new AtomicReference<>(0L);
        AtomicReference<Usage> usageRef = new AtomicReference<>();
        return chatClient.prompt()
                .system(PromptUtils.CODE_GENERATION_SYSTEM_PROMPT)
                .user(userMessage)
                .advisors(advisorSpec -> {
                            advisorSpec.params(advisorParams);
                            advisorSpec.advisors(fileTreeContextAdvisor);
                        }
                )
                .tools(codeGenerationTools)
                .stream()
                .chatResponse()
                // Usage arrives on the final usage-only chunk, which extractText drops,
                // so it has to be captured from the raw ChatResponse before mapping.
                .doOnNext(response -> {
                    Usage usage = response.getMetadata() != null ? response.getMetadata().getUsage() : null;
                    if (usage != null && usage.getTotalTokens() != null && usage.getTotalTokens() > 0) {
                        usageRef.set(usage);
                    }
                })
                .mapNotNull(AiGenerationServiceImpl::extractText)
                .filter(content -> !content.text().isEmpty())
                .doOnNext(content -> {
                            fullResponseBuffer.append(content.text());
                            if (endTime.get() == 0) {
                                endTime.set(System.currentTimeMillis());
                                //log.info("streamResponse time taken for projectId={} userId={} message={} is {} ms", projectId, userId, userMessage, endTime.get() - startTime);
                            }
                        }

                )
                .doOnComplete(() -> {
                    Schedulers.boundedElastic().schedule(() -> {
//                        parseAndSaveFiles(fullResponseBuffer.toString(), projectId);
                        long duration = (endTime.get() - startTime.get()) / 1000;
                        finalizeChat(userMessage, chatSession, fullResponseBuffer.toString(), duration, usageRef.get(),userId);

                    });

                })
                .doOnError(error -> {
                    log.error("Error in streamResponse", error);
                });
    }

    // The stream ends with a usage-only chunk that carries no generation at all,
    // so getResult() is null there. Dropping it keeps the stream alive long enough
    // to reach onComplete, which is what triggers parseAndSaveFiles.
    private static StreamResponse extractText(ChatResponse chatResponse) {
        Generation generation = chatResponse.getResult();
        if (generation == null || generation.getOutput() == null) {
            return null;
        }
        String text = generation.getOutput().getText();
        return text == null ? null : new StreamResponse(text);
    }

//    private void parseAndSaveFiles(String fullResponse, Long projectId) {
//        log.info("parseAndSaveFiles projectId={} responseLength={}", projectId, fullResponse.length());
//        log.debug("Full LLM response for projectId={}:\n{}", projectId, fullResponse);
//
//        Matcher matcher = FILE_TAG_PATTERN.matcher(fullResponse);
//
//        int saved = 0;
//        while (matcher.find()) {
//            String filePath = matcher.group(1);
//            String fileContent = matcher.group(2);
//            log.info("Parsed file tag path={} contentLength={}", filePath, fileContent.length());
//            try {
//                projectFileService.saveFile(projectId, filePath, fileContent);
//                saved++;
//            } catch (Exception e) {
//                log.error("Failed to persist generated file path={} projectId={}", filePath, projectId, e);
//            }
//        }
//
//        if (saved == 0) {
//            log.warn("No <file> tags found in the response for projectId={}. Nothing was written to Minio or the DB.", projectId);
//        } else {
//            log.info("Persisted {} generated file(s) for projectId={}", saved, projectId);
//        }
//    }

    // Create or ensure a chat session exists for the given project and user.
    // Kept private and simple here; real implementation should persist/fetch session entity.
    private ChatSession createChatSessionIfNotExist(Long projectId, Long userId) {
        ChatSessionId chatSessionId = new ChatSessionId(projectId, userId);
        ChatSession chatSession = chatSessionRepository.findById(chatSessionId).orElse(null);

        if(chatSession == null) {
            chatSession = ChatSession.builder()
                    .id(chatSessionId)
                    .build();

            chatSession = chatSessionRepository.save(chatSession);
        }
        return chatSession;
    }

    private void finalizeChat(String userMessage, ChatSession chatSession, String fullText, long duration, Usage usage, Long userId) {
        // Prompt tokens (system prompt + context + user message) are billed to the user message,
        // completion tokens to the assistant message.

        if(usage != null){
            int totalTokens = usage.getTotalTokens();
            usageService.recordTokenUsage(userId, totalTokens);
        }
        int promptTokens = usage != null && usage.getPromptTokens() != null ? usage.getPromptTokens() : 0;
        int completionTokens = usage != null && usage.getCompletionTokens() != null ? usage.getCompletionTokens() : 0;
        if (usage == null) {
            log.warn("No token usage reported for projectId={}", chatSession.getId().getProjectId());
        }

        //Save the User message
        long projectId = chatSession.getId().getProjectId();
        chatMessageRepository.save(
                ChatMessage.builder()
                        .chatSession(chatSession)
                        .role(MessageRole.USER)
                        .content(userMessage)
                        .tokensUsed(promptTokens)
                        .build()
        );

        ChatMessage assistantChatMessage = ChatMessage.builder()
                .content("Assistant message here ....")

                .role(MessageRole.ASSISTANT)
                .chatSession(chatSession)
                .tokensUsed(completionTokens)
                .build();
        chatMessageRepository.save(assistantChatMessage);

        List<ChatEvent> chatEventList = llmResponseParser.parseChatEvents(fullText, assistantChatMessage);
        chatEventList.addFirst(ChatEvent.builder()
                .type(ChatEventType.THOUGHT)
                .status(ChatEventStatus.CONFIRMED)
                .chatMessage(assistantChatMessage)
                .content("Thought for: " + duration + "s")
                .sequenceOrder(0)
                .build());
        chatEventList.stream()
                .filter(event -> event.getType() == ChatEventType.FILE_EDIT)
                .forEach(e -> {
                    String sagaId = UUID.randomUUID().toString();
                    e.setSagaId(sagaId);
                    FileStoreRequestEvent fileStoreRequestEvent = new FileStoreRequestEvent(
                            projectId,
                            sagaId,
                            e.getFilePath(),
                            e.getContent(),
                            userId
                    );
                    log.info("Storage request event sent: {}", e.getFilePath());
                    kafkaTemplate.send("file-storage-request-event", "project-"+projectId, fileStoreRequestEvent);
                });

        chatEventRepository.saveAll(chatEventList);
    }
}
