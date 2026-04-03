package com.ai.assistant.application.skill.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * SKILL.md 解析后的元数据
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillMetadata {

    /**
     * 技能名称
     */
    private String name;

    /**
     * 技能描述
     */
    private String description;

    /**
     * 触发关键词列表
     */
    private List<String> triggerKeywords;

    /**
     * 所需工具列表
     */
    private List<String> requiredTools;

    /**
     * 提示词模板
     */
    private String promptTemplate;

    /**
     * Agent 元数据（可选）
     */
    private AgentMetadata agent;
}