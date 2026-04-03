package com.ai.assistant.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 对话
 */
@Entity
@Table(name = "t_conversation")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 对话唯一标识
     */
    @Column(name = "conversation_id", unique = true, nullable = false, length = 64)
    private String conversationId;

    /**
     * 所属会话ID
     */
    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    /**
     * 对话标题
     */
    @Column(name = "title", length = 256)
    private String title;

    /**
     * 消息数量
     */
    @Column(name = "message_count", nullable = false)
    @Builder.Default
    private Integer messageCount = 0;

    /**
     * 是否已删除
     */
    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
