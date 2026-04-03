package com.ai.assistant.application.skill.dto;

import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 更新技能请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateSkillRequest {

    @Size(max = 128, message = "技能名称最长128字符")
    private String name;

    private String description;

    private String promptTemplate;

    private List<String> triggerKeywords;

    private List<String> requiredTools;
}
