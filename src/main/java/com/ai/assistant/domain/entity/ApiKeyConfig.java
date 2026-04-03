package com.ai.assistant.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * API Key 配置
 */
@Entity
@Table(name = "t_api_key_config")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 提供商标识
     * llm: deepseek, openai, claude
     * search: serper, tavily
     */
    @Column(name = "provider", unique = true, nullable = false, length = 32)
    private String provider;

    /**
     * 提供商类型
     */
    @Column(name = "provider_type", nullable = false, length = 32)
    private String providerType;

    /**
     * API Key (加密存储)
     */
    @Column(name = "api_key", columnDefinition = "TEXT")
    private String apiKey;

    /**
     * API 端点 (可选覆盖)
     */
    @Column(name = "api_endpoint", length = 512)
    private String apiEndpoint;

    /**
     * 是否启用
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 配置备注
     */
    @Column(name = "remark", length = 256)
    private String remark;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
