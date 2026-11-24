package com.avish.distributed_craftly.intelligence_service.llm.advisors;

import com.avish.distributed_craftly.common_lib.dto.FileNode;
import com.avish.distributed_craftly.intelligence_service.client.WorkspaceClient;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.client.ChatClientResponse;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisorChain;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;


@RequiredArgsConstructor
@Slf4j
@Component
public class FileTreeContextAdvisor implements StreamAdvisor {
    //private final ProjectFileService projectFileService;
    private final WorkspaceClient workspaceClient;

    @Override
    public String getName() {
        return "FileTreeContextAdvisor";
    }

    @Override
    public int getOrder() {
        return 0;
    }

    @Override
    public Flux<ChatClientResponse> adviseStream(ChatClientRequest request, StreamAdvisorChain streamAdvisorChain) {
        Map<String,Object> context = request.context();
        Long projectId = (Long) context.getOrDefault("projectId",0);
        ChatClientRequest augmentedChatClientRequest = augmentChatClientRequestWithFileTree(request, projectId);
        return streamAdvisorChain.nextStream(augmentedChatClientRequest);
    }

    private ChatClientRequest augmentChatClientRequestWithFileTree(ChatClientRequest request, Long projectId) {
        List<Message> incomingMessages = request.prompt().getInstructions();
        Message systemMessage = incomingMessages
                .stream()
                .filter(m->m.getMessageType() == MessageType.SYSTEM)
                .findFirst()
                .orElse(null);
        List<Message> userMessages = incomingMessages
                .stream()
                .filter(m->m.getMessageType() != MessageType.SYSTEM)
                .toList();

        List<Message> allMessages = new ArrayList<>();
        if (systemMessage != null) {
            allMessages.add(systemMessage);
        }
        List<FileNode> fileNodes = workspaceClient.getFileTree(projectId).files();
        String fileTreeContext = fileNodes.isEmpty()
                ? "\n\n---- FILE_TREE ----\n(empty - this is a brand new project, create every file from scratch and read nothing)"
                : "\n\n---- FILE_TREE ----\n" + fileNodes.stream()
                        .map(FileNode::path)
                        .sorted()
                        .collect(Collectors.joining("\n"));

        log.debug("Injecting FILE_TREE for projectId={} with {} file(s)", projectId, fileNodes.size());
        allMessages.add(new SystemMessage(fileTreeContext));
        allMessages.addAll(userMessages);

        return request
                .mutate()
                .prompt(new Prompt(allMessages, request.prompt().getOptions()))
                .build();
    }
}
