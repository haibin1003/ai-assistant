package com.ai.assistant.domain.entity;

import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

/**
 * 工具缓存
 */
@Entity
@Table(name = "t_tool_cache")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 系统ID
     */
    @Column(name = "system_id", nullable = false, length = 64)
    private String systemId;

    /**
     * 工具名称
     */
    @Column(name = "tool_name", nullable = false, length = 128)
    private String toolName;

    /**
     * 工具描述
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * JSON 格式的输入 Schema
     */
    @Column(name = "input_schema", nullable = false, columnDefinition = "TEXT")
    private String inputSchema;

    @CreatedDate
    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;
}
