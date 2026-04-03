package com.ai.assistant.interfaces.rest;

import com.ai.assistant.application.skill.SkillLoaderService;
import com.ai.assistant.application.skill.SkillPackageService;
import com.ai.assistant.common.response.ApiResponse;
import com.ai.assistant.domain.entity.SkillPackage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;

/**
 * Skill 包 REST 接口
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/skill-packages")
@RequiredArgsConstructor
public class SkillPackageController {

    private final SkillPackageService skillPackageService;
    private final SkillLoaderService skillLoaderService;

    /**
     * 获取所有已发布的 Skills
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<SkillPackage>>> getAllSkills() {
        List<SkillPackage> skills = skillPackageService.getPublishedSkills();
        return ResponseEntity.ok(ApiResponse.success(skills));
    }

    /**
     * 根据 skillId 获取 Skill
     */
    @GetMapping("/{skillId}")
    public ResponseEntity<ApiResponse<SkillPackage>> getSkill(@PathVariable String skillId) {
        SkillPackage skill = skillPackageService.getSkillById(skillId);
        return ResponseEntity.ok(ApiResponse.success(skill));
    }

    /**
     * 获取 Skill 的 SKILL.md 内容
     */
    @GetMapping("/{skillId}/content")
    public ResponseEntity<ApiResponse<String>> getSkillContent(@PathVariable String skillId) {
        SkillPackage skill = skillPackageService.getSkillById(skillId);
        String content = skillPackageService.getSkillMdContent(skill);
        return ResponseEntity.ok(ApiResponse.success(content));
    }

    /**
     * 获取 Skill 的目录结构（scripts、references、assets 文件列表）
     */
    @GetMapping("/{skillId}/files")
    public ResponseEntity<ApiResponse<Object>> getSkillFiles(@PathVariable String skillId) {
        SkillPackage skill = skillPackageService.getSkillById(skillId);
        var structure = skillPackageService.getSkillDirectoryStructure(skill);
        return ResponseEntity.ok(ApiResponse.success(structure));
    }

    /**
     * 获取 Skill 指定文件的内容
     */
    @GetMapping("/{skillId}/file")
    public ResponseEntity<ApiResponse<String>> getSkillFileContent(
            @PathVariable String skillId,
            @RequestParam("path") String filePath) {
        SkillPackage skill = skillPackageService.getSkillById(skillId);
        String content = skillPackageService.getFileContentByPath(skill.getStoragePath(), filePath);
        return ResponseEntity.ok(ApiResponse.success(content));
    }

    /**
     * 创建并发布 Skill
     */
    @PostMapping
    public ResponseEntity<ApiResponse<SkillPackage>> createSkill(
            @RequestParam("name") String name,
            @RequestParam("description") String description,
            @RequestParam("skillMdContent") String skillMdContent,
            @RequestParam(value = "triggerKeywords", required = false) String[] triggerKeywords,
            @RequestParam(value = "requiredTools", required = false) String[] requiredTools,
            @RequestParam(value = "scripts", required = false) MultipartFile[] scripts,
            @RequestParam(value = "references", required = false) MultipartFile[] references,
            @RequestParam(value = "assets", required = false) MultipartFile[] assets,
            @RequestHeader(value = "X-User-Id", defaultValue = "anonymous") String userId
    ) {
        log.info("Creating skill: name={}, user={}", name, userId);

        List<String> triggers = triggerKeywords != null ? List.of(triggerKeywords) : List.of();
        List<String> tools = requiredTools != null ? List.of(requiredTools) : List.of();

        SkillPackage skill = skillPackageService.createAndPublish(
                name,
                description,
                skillMdContent,
                triggers,
                tools,
                scripts,
                references,
                assets,
                userId
        );

        return ResponseEntity.ok(ApiResponse.success(skill));
    }

    /**
     * 删除 Skill
     */
    @DeleteMapping("/{skillId}")
    public ResponseEntity<ApiResponse<Void>> deleteSkill(@PathVariable String skillId) {
        log.info("Deleting skill: skillId={}", skillId);
        skillPackageService.deleteSkill(skillId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 重新加载 Skill（触发热加载）
     */
    @PostMapping("/{skillId}/reload")
    public ResponseEntity<ApiResponse<String>> reloadSkill(@PathVariable String skillId) {
        log.info("Reloading skill: skillId={}", skillId);
        // 重新加载 Skill 元数据并更新缓存
        skillLoaderService.reloadSkill(skillId);
        return ResponseEntity.ok(ApiResponse.success("Skill reloaded successfully"));
    }
}