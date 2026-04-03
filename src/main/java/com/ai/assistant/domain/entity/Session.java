package com.ai.assistant.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 用户会话
 */
@Entity
@Table(name = "t_session")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 会话唯一标识
     */
    @Column(name = "session_id", unique = true, nullable = false, length = 64)
    private String sessionId;

    /**
     * 目标系统ID
     */
    @Column(name = "system_id", nullable = false, length = 64)
    private String systemId;

    /**
     * 用户ID
     */
    @Column(name = "user_id", length = 64)
    private String userId;

    /**
     * 用户名
     */
    @Column(name = "username", length = 128)
    private String username;

    /**
     * 用户上下文 JSON
     */
    @Column(name = "user_context", columnDefinition = "TEXT")
    private String userContext;

    /**
     * 访问令牌 (加密存储)
     */
    @Column(name = "access_token", columnDefinition = "TEXT")
    private String accessToken;

    /**
     * 刷新令牌 (加密存储)
     */
    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    /**
     * 令牌过期时间
     */
    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    /**
     * Basic Auth 用户名 (加密存储)
     */
    @Column(name = "auth_username", length = 128)
    private String authUsername;

    /**
     * Basic Auth 密码 (加密存储)
     */
    @Column(name = "auth_password", columnDefinition = "TEXT")
    private String authPassword;

    /**
     * 会话过期时间
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

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

    /**
     * 会话是否过期
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 令牌是否过期
     */
    public boolean isTokenExpired() {
        return tokenExpiresAt != null && LocalDateTime.now().isAfter(tokenExpiresAt);
    }
}
