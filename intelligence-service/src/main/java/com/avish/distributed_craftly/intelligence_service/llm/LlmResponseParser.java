package com.avish.distributed_craftly.intelligence_service.llm;

import com.avish.distributed_craftly.common_lib.enums.ChatEventStatus;
import com.avish.distributed_craftly.common_lib.enums.ChatEventType;
import com.avish.distributed_craftly.intelligence_service.entity.ChatEvent;
import com.avish.distributed_craftly.intelligence_service.entity.ChatMessage;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Component
@Slf4j
public class LlmResponseParser {

    /**
     * Regex Breakdown:
     * Group 1: Opening Tag (<tag ...>)
     * Group 2: Tag Name (message|file|tool)
     * Group 3: Attributes part (e.g., ' path="foo"' or ' args="a,b"')
     * Group 4: Content (The stuff inside)
     * Group 5: Closing Tag (</tag>)
     */

    private static final Pattern GENERIC_TAG_PATTERN = Pattern.compile(
            "(<(message|file|tool)([^>]*)>)([\\s\\S]*?)(</\\2>)",
            Pattern.CASE_INSENSITIVE | Pattern.DOTALL
    );

    // Helper to extract specific attributes (path="..." or args="...") from Group 3
    private static final Pattern ATTRIBUTE_PATTERN = Pattern.compile(
            "(path|args)=\"([^\"]+)\""
    );

    // An opening markdown fence: ``` on its own or followed by a language tag (```tsx)
    private static final Pattern OPENING_FENCE_PATTERN = Pattern.compile("^```[a-zA-Z0-9+#-]*$");

    public List<ChatEvent> parseChatEvents(String fullResponse, ChatMessage parentMessage) {
        List<ChatEvent> events = new ArrayList<>();
        int orderCounter = 1;

        Matcher matcher = GENERIC_TAG_PATTERN.matcher(fullResponse);

        while (matcher.find()) {
            String tagName = matcher.group(2).toLowerCase();
            String attributes = matcher.group(3);
            String content = matcher.group(4).trim();

            // Extract attributes map
            Map<String, String> attrMap = extractAttributes(attributes);

            ChatEvent.ChatEventBuilder builder = ChatEvent.builder()
                    .status(ChatEventStatus.CONFIRMED)
                    .chatMessage(parentMessage)
                    .content(content) // This is your Markdown content
                    .sequenceOrder(orderCounter++);

            switch (tagName) {
                case "message" -> builder.type(ChatEventType.MESSAGE);
                case "file" -> {
                    builder.type(ChatEventType.FILE_EDIT);
                    builder.status(ChatEventStatus.PENDING); // Files are pending until stored
                    builder.filePath(attrMap.get("path")); // Required for files
                    // The model sometimes wraps file content in a markdown fence despite the
                    // prompt. Written as-is it makes the file unparseable, so strip it here -
                    // this is the single place file content reaches both Minio and the DB.
                    builder.content(stripCodeFence(content, attrMap.get("path")));
                }
                case "tool" -> {
                    builder.type(ChatEventType.TOOL_LOG);
                    builder.metadata(attrMap.get("args")); // Store raw file list in metadata
                }
                default -> { continue; }
            }

            events.add(builder.build());
        }

        return events;
    }

    /**
     * Removes a markdown code fence wrapping the whole file content, e.g. an opening
     * ```tsx line and the matching closing ``` line. Content that is not fenced, or that
     * merely contains a fence somewhere in the middle, is returned untouched.
     */
    private String stripCodeFence(String content, String filePath) {
        if (!content.startsWith("```")) {
            return content;
        }

        int firstLineEnd = content.indexOf('\n');
        if (firstLineEnd < 0) {
            return content;
        }
        // Only an opening fence: ``` optionally followed by a language tag (```tsx, ```ts).
        if (!OPENING_FENCE_PATTERN.matcher(content.substring(0, firstLineEnd).trim()).matches()) {
            return content;
        }

        String body = content.substring(firstLineEnd + 1);
        int lastFence = body.lastIndexOf("```");
        if (lastFence >= 0 && body.substring(lastFence + 3).isBlank()) {
            body = body.substring(0, lastFence);
        }

        log.warn("Stripped markdown code fence from generated file path={}", filePath);
        return body.strip();
    }

    private Map<String, String> extractAttributes(String attributeString) {
        Map<String, String> attributes = new HashMap<>();
        if (attributeString == null) return attributes;

        Matcher matcher = ATTRIBUTE_PATTERN.matcher(attributeString);
        while (matcher.find()) {
            attributes.put(matcher.group(1), matcher.group(2));
        }
        return attributes;
    }

}

