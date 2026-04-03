package com.ai.assistant.domain.entity;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.*;
import lombok.*;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

/**
 * 技能定义
 */
@Entity
@Table(name = "t_skill")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Skill {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 技能唯一标识
     */
    @Column(name = "skill_id", unique = true, nullable = false, length = 64)
    private String skillId;

    /**
     * 技能名称
     */
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /**
     * 技能描述
     */
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    /**
     * 提示词模板
     */
    @Column(name = "prompt_template", nullable = false, columnDefinition = "TEXT")
    private String promptTemplate;

    /**
     * 触发关键词 (JSON 数组)
     */
    @Column(name = "trigger_keywords", columnDefinition = "TEXT")
    private String triggerKeywords;

    /**
     * 所需工具列表 (JSON 数组)
     */
    @Column(name = "required_tools", columnDefinition = "TEXT")
    private String requiredTools;

    /**
     * 是否全局技能
     */
    @Column(name = "is_global", nullable = false)
    @Builder.Default
    private Boolean isGlobal = false;

    /**
     * 是否启用
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 创建人用户ID
     */
    @Column(name = "created_by", length = 64)
    private String createdBy;

    /**
     * 所属系统ID
     */
    @Column(name = "system_id", length = 64)
    private String systemId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 获取触发关键词列表
     */
    public List<String> getTriggerKeywordList() {
        if (triggerKeywords == null || triggerKeywords.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(triggerKeywords, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    /**
     * 获取所需工具列表
     */
    public List<String> getRequiredToolList() {
        if (requiredTools == null || requiredTools.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return OBJECT_MAPPER.readValue(requiredTools, new TypeReference<List<String>>() {});
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }
}
