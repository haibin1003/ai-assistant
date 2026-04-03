package com.ai.assistant.application.skill.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 创建技能请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateSkillRequest {

    @NotBlank(message = "技能名称不能为空")
    @Size(max = 128, message = "技能名称最长128字符")
    private String name;

    @NotBlank(message = "技能描述不能为空")
    private String description;

    @NotBlank(message = "提示词模板不能为空")
    private String promptTemplate;

    private List<String> triggerKeywords;

    private List<String> requiredTools;

    private Boolean isGlobal;

    private String systemId;
}
