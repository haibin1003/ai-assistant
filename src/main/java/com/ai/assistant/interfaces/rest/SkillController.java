package com.ai.assistant.interfaces.rest;

import com.ai.assistant.application.context.ContextService;
import com.ai.assistant.application.context.dto.UserContextDTO;
import com.ai.assistant.application.skill.SkillService;
import com.ai.assistant.application.skill.dto.CreateSkillRequest;
import com.ai.assistant.application.skill.dto.SkillDTO;
import com.ai.assistant.application.skill.dto.UpdateSkillRequest;
import com.ai.assistant.common.exception.BizException;
import com.ai.assistant.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 技能管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/skills")
@RequiredArgsConstructor
public class SkillController {

    private final SkillService skillService;
    private final ContextService contextService;

    /**
     * 获取可用技能列表
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SkillDTO>>> getAvailableSkills(
            @RequestHeader(value = "X-Session-Id", required = false) String sessionId) {

        UserContextDTO context = sessionId != null ? contextService.getContext(sessionId) : null;
        List<SkillDTO> skills = skillService.getAvailableSkills(context);
        return ResponseEntity.ok(ApiResponse.success(skills));
    }

    /**
     * 创建技能
     */
    @PostMapping
    public ResponseEntity<ApiResponse<SkillDTO>> createSkill(
            @Valid @RequestBody CreateSkillRequest request,
            @RequestHeader("X-Session-Id") String sessionId) {

        UserContextDTO context = contextService.getContext(sessionId);
        if (context == null) {
            throw new BizException("SESSION_INVALID", "Invalid session");
        }

        SkillDTO skill = skillService.createSkill(request, context);
        return ResponseEntity.ok(ApiResponse.success(skill));
    }

    /**
     * 获取技能详情
     */
    @GetMapping("/{skillId}")
    public ResponseEntity<ApiResponse<SkillDTO>> getSkill(@PathVariable String skillId) {
        SkillDTO skill = skillService.getSkill(skillId);
        return ResponseEntity.ok(ApiResponse.success(skill));
    }

    /**
     * 更新技能
     */
    @PutMapping("/{skillId}")
    public ResponseEntity<ApiResponse<SkillDTO>> updateSkill(
            @PathVariable String skillId,
            @Valid @RequestBody UpdateSkillRequest request,
            @RequestHeader("X-Session-Id") String sessionId) {

        UserContextDTO context = contextService.getContext(sessionId);
        if (context == null) {
            throw new BizException("SESSION_INVALID", "Invalid session");
        }

        SkillDTO skill = skillService.updateSkill(skillId, request, context);
        return ResponseEntity.ok(ApiResponse.success(skill));
    }

    /**
     * 删除技能
     */
    @DeleteMapping("/{skillId}")
    public ResponseEntity<ApiResponse<Void>> deleteSkill(
            @PathVariable String skillId,
            @RequestHeader("X-Session-Id") String sessionId) {

        UserContextDTO context = contextService.getContext(sessionId);
        if (context == null) {
            throw new BizException("SESSION_INVALID", "Invalid session");
        }

        skillService.deleteSkill(skillId, context);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 获取用户创建的技能
     */
    @GetMapping("/my")
    public ResponseEntity<ApiResponse<List<SkillDTO>>> getMySkills(
            @RequestHeader("X-Session-Id") String sessionId) {

        UserContextDTO context = contextService.getContext(sessionId);
        if (context == null) {
            throw new BizException("SESSION_INVALID", "Invalid session");
        }

        List<SkillDTO> skills = skillService.getUserSkills(context.getUserId());
        return ResponseEntity.ok(ApiResponse.success(skills));
    }

    /**
     * 获取全局技能列表
     */
    @GetMapping("/global")
    public ResponseEntity<ApiResponse<List<SkillDTO>>> getGlobalSkills() {
        List<SkillDTO> skills = skillService.getGlobalSkills();
        return ResponseEntity.ok(ApiResponse.success(skills));
    }
}
