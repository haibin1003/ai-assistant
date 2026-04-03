package com.ai.assistant.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 注册的目标系统
 */
@Entity
@Table(name = "t_registered_system")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisteredSystem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 系统唯一标识
     */
    @Column(name = "system_id", unique = true, nullable = false, length = 64)
    private String systemId;

    /**
     * 系统名称
     */
    @Column(name = "system_name", nullable = false, length = 128)
    private String systemName;

    /**
     * 系统图标 URL
     */
    @Column(name = "icon_url", length = 512)
    private String iconUrl;

    /**
     * MCP 网关地址
     */
    @Column(name = "mcp_gateway_url", nullable = false, length = 512)
    private String mcpGatewayUrl;

    /**
     * 认证类型: none, basic, bearer
     */
    @Column(name = "auth_type", nullable = false, length = 32)
    @Builder.Default
    private String authType = "none";

    /**
     * 工具名前缀
     */
    @Column(name = "tool_prefix", nullable = false, length = 32)
    private String toolPrefix;

    /**
     * 系统描述
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 是否启用
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
