package com.ai.assistant.application.skill.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Agent 元数据（对应 agents/openai.yaml）
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AgentMetadata {

    /**
     * Agent 名称
     */
    private String name;

    /**
     * Agent 描述
     */
    private String description;

    /**
     * 角色/指令
     */
    private String instructions;

    /**
     * 工具列表
     */
    private java.util.List<String> tools;
}