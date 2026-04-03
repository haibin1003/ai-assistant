package com.ai.assistant.application.skill;

import com.ai.assistant.application.skill.dto.CreateSkillRequest;
import com.ai.assistant.application.skill.dto.SkillDTO;
import com.ai.assistant.application.skill.dto.UpdateSkillRequest;
import com.ai.assistant.application.context.dto.UserContextDTO;
import com.ai.assistant.common.exception.BizException;
import com.ai.assistant.domain.entity.Skill;
import com.ai.assistant.domain.repository.SkillRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 技能管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SkillService {

    private final SkillRepository skillRepository;
    private final ObjectMapper objectMapper;

    /**
     * 创建技能
     */
    @Transactional
    public SkillDTO createSkill(CreateSkillRequest request, UserContextDTO context) {
        if (Boolean.TRUE.equals(request.getIsGlobal()) && !isAdmin(context)) {
            throw new BizException("PERMISSION_DENIED", "Only admin can create global skills");
        }

        String skillId = generateSkillId(request.getIsGlobal(), context != null ? context.getUserId() : null);

        Skill skill = Skill.builder()
            .skillId(skillId)
            .name(request.getName())
            .description(request.getDescription())
            .promptTemplate(request.getPromptTemplate())
            .triggerKeywords(toJson(request.getTriggerKeywords()))
            .requiredTools(toJson(request.getRequiredTools()))
            .isGlobal(request.getIsGlobal() != null ? request.getIsGlobal() : false)
            .isActive(true)
            .createdBy(context != null ? context.getUserId() : null)
            .systemId(request.getSystemId())
            .build();

        skillRepository.save(skill);

        log.info("Skill created: {}, isGlobal: {}", skill.getSkillId(), skill.getIsGlobal());
        return toDTO(skill);
    }

    /**
     * 更新技能
     */
    @Transactional
    public SkillDTO updateSkill(String skillId, UpdateSkillRequest request, UserContextDTO context) {
        Skill skill = skillRepository.findBySkillId(skillId)
            .orElseThrow(() -> new BizException("SKILL_NOT_FOUND", "Skill not found: " + skillId));

        if (!canModify(skill, context)) {
            throw new BizException("PERMISSION_DENIED", "You don't have permission to modify this skill");
        }

        if (request.getName() != null) {
            skill.setName(request.getName());
        }
        if (request.getDescription() != null) {
            skill.setDescription(request.getDescription());
        }
        if (request.getPromptTemplate() != null) {
            skill.setPromptTemplate(request.getPromptTemplate());
        }
        if (request.getTriggerKeywords() != null) {
            skill.setTriggerKeywords(toJson(request.getTriggerKeywords()));
        }
        if (request.getRequiredTools() != null) {
            skill.setRequiredTools(toJson(request.getRequiredTools()));
        }

        skillRepository.save(skill);

        log.info("Skill updated: {}", skillId);
        return toDTO(skill);
    }

    /**
     * 删除技能
     */
    @Transactional
    public void deleteSkill(String skillId, UserContextDTO context) {
        Skill skill = skillRepository.findBySkillId(skillId)
            .orElseThrow(() -> new BizException("SKILL_NOT_FOUND", "Skill not found: " + skillId));

        if (!canModify(skill, context)) {
            throw new BizException("PERMISSION_DENIED", "You don't have permission to delete this skill");
        }

        skill.setIsActive(false);
        skillRepository.save(skill);

        log.info("Skill deleted: {}", skillId);
    }

    /**
     * 获取技能详情
     */
    public SkillDTO getSkill(String skillId) {
        Skill skill = skillRepository.findBySkillId(skillId)
            .orElseThrow(() -> new BizException("SKILL_NOT_FOUND", "Skill not found: " + skillId));
        return toDTO(skill);
    }

    /**
     * 获取可用技能列表
     */
    public List<SkillDTO> getAvailableSkills(UserContextDTO context) {
        List<Skill> skills = new ArrayList<>();

        skills.addAll(skillRepository.findByIsGlobalTrueAndIsActiveTrue());

        if (context != null && context.getUserId() != null) {
            skills.addAll(skillRepository.findByCreatedByAndIsActiveTrue(context.getUserId()));
        }

        return skills.stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * 获取用户创建的技能
     */
    public List<SkillDTO> getUserSkills(String userId) {
        return skillRepository.findByCreatedByAndIsActiveTrue(userId).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * 获取全局技能列表
     */
    public List<SkillDTO> getGlobalSkills() {
        return skillRepository.findByIsGlobalTrueAndIsActiveTrue().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    private String generateSkillId(boolean isGlobal, String userId) {
        String prefix = isGlobal ? "global" : "user";
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return prefix + "_" + suffix;
    }

    private boolean isAdmin(UserContextDTO context) {
        return context != null && context.getRoles() != null &&
            context.getRoles().contains("ADMIN");
    }

    private boolean canModify(Skill skill, UserContextDTO context) {
        if (isAdmin(context)) {
            return true;
        }
        return !skill.getIsGlobal() &&
            skill.getCreatedBy() != null &&
            skill.getCreatedBy().equals(context != null ? context.getUserId() : null);
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private SkillDTO toDTO(Skill skill) {
        return SkillDTO.builder()
            .skillId(skill.getSkillId())
            .name(skill.getName())
            .description(skill.getDescription())
            .promptTemplate(skill.getPromptTemplate())
            .triggerKeywords(skill.getTriggerKeywordList())
            .requiredTools(skill.getRequiredToolList())
            .isGlobal(skill.getIsGlobal())
            .createdBy(skill.getCreatedBy())
            .systemId(skill.getSystemId())
            .createdAt(skill.getCreatedAt())
            .updatedAt(skill.getUpdatedAt())
            .build();
    }
}
