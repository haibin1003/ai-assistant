package com.ai.assistant.application.chat;

import com.ai.assistant.application.chat.dto.ConversationDTO;
import com.ai.assistant.application.chat.dto.MessageDTO;
import com.ai.assistant.common.exception.BizException;
import com.ai.assistant.domain.entity.Conversation;
import com.ai.assistant.domain.entity.Message;
import com.ai.assistant.domain.repository.ConversationRepository;
import com.ai.assistant.domain.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 对话管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    /**
     * 获取或创建对话
     */
    @Transactional
    public Conversation getOrCreateConversation(String sessionId, String conversationId) {
        if (conversationId != null) {
            return conversationRepository.findByConversationId(conversationId)
                .orElseGet(() -> createConversation(sessionId));
        }
        return createConversation(sessionId);
    }

    /**
     * 创建新对话
     */
    @Transactional
    public Conversation createConversation(String sessionId) {
        Conversation conversation = Conversation.builder()
            .conversationId(generateConversationId())
            .sessionId(sessionId)
            .messageCount(0)
            .isDeleted(false)
            .build();

        return conversationRepository.save(conversation);
    }

    /**
     * 添加消息
     */
    @Transactional
    public Message addMessage(String conversationId, String role, String content) {
        Message message = Message.builder()
            .conversationId(conversationId)
            .role(role)
            .content(content)
            .build();

        messageRepository.save(message);
        conversationRepository.incrementMessageCount(conversationId);

        return message;
    }

    /**
     * 添加工具调用消息
     */
    @Transactional
    public Message addToolMessage(String conversationId, String toolCallId,
                                   String toolName, String content) {
        Message message = Message.builder()
            .conversationId(conversationId)
            .role("tool")
            .content(content)
            .toolCallId(toolCallId)
            .toolName(toolName)
            .build();

        messageRepository.save(message);
        conversationRepository.incrementMessageCount(conversationId);

        return message;
    }

    /**
     * 获取最近消息
     */
    public List<Message> getRecentMessages(String conversationId, int limit) {
        return messageRepository.findTopNByConversationIdOrderByCreatedAtDesc(
            conversationId, PageRequest.of(0, limit));
    }

    /**
     * 获取用户对话列表
     */
    public List<ConversationDTO> getUserConversations(String sessionId) {
        return conversationRepository.findBySessionIdAndIsDeletedFalseOrderByUpdatedAtDesc(sessionId)
            .stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * 获取对话详情
     */
    public ConversationDTO getConversationDTO(String conversationId) {
        return conversationRepository.findByConversationId(conversationId)
            .map(this::toDTO)
            .orElseThrow(() -> new BizException("CONVERSATION_NOT_FOUND",
                "Conversation not found: " + conversationId));
    }

    /**
     * 删除对话
     */
    @Transactional
    public void deleteConversation(String conversationId, String sessionId) {
        conversationRepository.findByConversationIdAndSessionId(conversationId, sessionId)
            .ifPresent(conv -> {
                conv.setIsDeleted(true);
                conversationRepository.save(conv);
            });
    }

    /**
     * 更新对话标题
     */
    @Transactional
    public void updateTitle(String conversationId, String title) {
        conversationRepository.updateTitle(conversationId, title);
    }

    private String generateConversationId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private ConversationDTO toDTO(Conversation conv) {
        return ConversationDTO.builder()
            .conversationId(conv.getConversationId())
            .title(conv.getTitle())
            .messageCount(conv.getMessageCount())
            .createdAt(conv.getCreatedAt())
            .updatedAt(conv.getUpdatedAt())
            .build();
    }
}
