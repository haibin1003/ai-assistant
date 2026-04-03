package com.ai.assistant.interfaces.rest;

import com.ai.assistant.application.chat.ChatService;
import com.ai.assistant.application.chat.ConversationService;
import com.ai.assistant.application.chat.dto.*;
import com.ai.assistant.application.context.ContextService;
import com.ai.assistant.application.context.dto.UserContextDTO;
import com.ai.assistant.common.exception.BizException;
import com.ai.assistant.common.response.ApiResponse;
import com.ai.assistant.domain.entity.Message;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 对话控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ChatController {

    private final ChatService chatService;
    private final ConversationService conversationService;
    private final ContextService contextService;

    /**
     * 流式对话 (SSE)
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatEvent>> chat(
            @Valid @RequestBody ChatRequest request,
            @RequestHeader("X-Session-Id") String sessionId) {

        log.info("Chat request from session: {}", sessionId);
        return chatService.chatStream(sessionId, request);
    }

    /**
     * 获取对话列表
     */
    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<List<ConversationDTO>>> getConversations(
            @RequestHeader("X-Session-Id") String sessionId) {

        UserContextDTO context = contextService.getContext(sessionId);
        if (context == null) {
            throw new BizException("SESSION_INVALID", "Invalid session");
        }

        List<ConversationDTO> conversations = conversationService.getUserConversations(sessionId);
        return ResponseEntity.ok(ApiResponse.success(conversations));
    }

    /**
     * 获取对话详情
     */
    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<ApiResponse<ConversationDetailDTO>> getConversation(
            @PathVariable String conversationId,
            @RequestHeader("X-Session-Id") String sessionId) {

        UserContextDTO context = contextService.getContext(sessionId);
        if (context == null) {
            throw new BizException("SESSION_INVALID", "Invalid session");
        }

        ConversationDTO conversation = conversationService.getConversationDTO(conversationId);
        List<Message> messages = conversationService.getRecentMessages(conversationId, 100);

        ConversationDetailDTO detail = ConversationDetailDTO.builder()
            .conversation(conversation)
            .messages(messages.stream().map(this::toMessageDTO).collect(Collectors.toList()))
            .build();

        return ResponseEntity.ok(ApiResponse.success(detail));
    }

    /**
     * 删除对话
     */
    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(
            @PathVariable String conversationId,
            @RequestHeader("X-Session-Id") String sessionId) {

        conversationService.deleteConversation(conversationId, sessionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    private MessageDTO toMessageDTO(Message msg) {
        return MessageDTO.builder()
            .messageId(msg.getMessageId())
            .role(msg.getRole())
            .content(msg.getContent())
            .toolName(msg.getToolName())
            .createdAt(msg.getCreatedAt())
            .build();
    }
}
