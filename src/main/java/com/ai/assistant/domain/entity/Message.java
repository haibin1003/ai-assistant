package com.ai.assistant.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * 消息
 */
@Entity
@Table(name = "t_message")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 消息唯一标识
     */
    @Column(name = "message_id", unique = true, nullable = false, length = 64)
    private String messageId;

    /**
     * 所属对话ID
     */
    @Column(name = "conversation_id", nullable = false, length = 64)
    private String conversationId;

    /**
     * 角色: user / assistant / tool
     */
    @Column(name = "role", nullable = false, length = 32)
    private String role;

    /**
     * 消息内容
     */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /**
     * 工具调用 (JSON)
     */
    @Column(name = "tool_calls", columnDefinition = "TEXT")
    private String toolCalls;

    /**
     * 工具调用ID
     */
    @Column(name = "tool_call_id", length = 64)
    private String toolCallId;

    /**
     * 工具名称
     */
    @Column(name = "tool_name", length = 128)
    private String toolName;

    @CreatedDate
    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (messageId == null) {
            messageId = UUID.randomUUID().toString().replace("-", "");
        }
    }
}
