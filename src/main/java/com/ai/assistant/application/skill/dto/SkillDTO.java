package com.ai.assistant.application.skill.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 技能 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillDTO {

    private String skillId;
    private String name;
    private String description;
    private String promptTemplate;
    private List<String> triggerKeywords;
    private List<String> requiredTools;
    private Boolean isGlobal;
    private String createdBy;
    private String systemId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
