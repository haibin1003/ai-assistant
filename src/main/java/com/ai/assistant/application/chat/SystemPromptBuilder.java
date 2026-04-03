package com.ai.assistant.application.chat;

import com.ai.assistant.application.context.dto.UserContextDTO;
import com.ai.assistant.application.skill.SkillLoaderService;
import com.ai.assistant.application.skill.dto.SkillMetadata;
import com.ai.assistant.application.system.SystemService;
import com.ai.assistant.infrastructure.mcp.dto.ToolDefinition;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * System Prompt 构建服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemPromptBuilder {

    private final SystemService systemService;
    private SkillLoaderService skillLoaderService;

    @Autowired(required = false)
    public void setSkillLoaderService(SkillLoaderService skillLoaderService) {
        this.skillLoaderService = skillLoaderService;
    }

    private static final String BASE_PROMPT = """
        你是一个名为"OSRM智能助手"的AI助手，当前已接入以下系统：
        - **OSRM**（开源软件仓库管理系统）：提供软件包管理、订购审批、业务系统管理等企业级功能

        ## 自我介绍规范
        当用户要求你介绍自己时，请简洁回复（不超过3-5句话），格式如下：
        "你好！我是OSRM智能助手，一个专注于开源软件管理的AI助手。我接入了OSRM平台，可以帮助你完成软件搜索、订购、审批等操作。有什么可以帮你的吗？"

        ## 工具使用规范
        - 优先使用工具完成操作，不要仅凭猜测回答
        - 工具调用应简洁、目的明确
        - 游客用户只能使用公开工具，管理员可使用所有工具
        """;

    /**
     * 构建完整的 System Prompt
     */
    public String buildSystemPrompt(UserContextDTO context) {
        if (context == null) {
            return BASE_PROMPT;
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append(BASE_PROMPT).append("\n\n");
        prompt.append(context.toSystemPromptSection()).append("\n\n");

        // 添加可用工具说明
        String toolsInfo = buildToolsInfo(context);
        prompt.append(toolsInfo);

        // 添加已加载的 Skills 说明
        String skillsInfo = buildSkillsInfo();
        if (!skillsInfo.isEmpty()) {
            prompt.append("\n\n").append(skillsInfo);
        }

        return prompt.toString();
    }

    private String buildToolsInfo(UserContextDTO context) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 可用工具\n\n");

        if (context.getSystemId() != null) {
            try {
                // 检查是否登录（游客模式只能使用公开工具）
                boolean isLoggedIn = context.getUserId() != null && !"guest".equals(context.getUserId());

                List<ToolDefinition> tools;
                if (isLoggedIn && context.getAuthUsername() != null && context.getAuthPassword() != null) {
                    // 登录用户：获取系统的所有工具（包括管理工具）
                    tools = systemService.getSystemTools(
                        context.getSystemId(),
                        context.getAuthUsername(),
                        context.getAuthPassword());
                } else {
                    // 游客：只获取公开工具
                    tools = systemService.getSystemTools(context.getSystemId());
                }

                for (ToolDefinition tool : tools) {
                    sb.append("- **").append(tool.getName()).append("**: ")
                      .append(tool.getDescription() != null ? tool.getDescription() : "无描述")
                      .append("\n");
                }
            } catch (Exception e) {
                log.warn("Failed to get system tools", e);
            }
        }

        // 添加内置工具
        sb.append("\n### 内置工具\n");
        sb.append("- **web_search**: 搜索网络信息\n");
        sb.append("- **browser_navigate**: 打开网页\n");
        sb.append("- **browser_snapshot**: 获取页面内容\n");

        return sb.toString();
    }

    private String buildSkillsInfo() {
        if (skillLoaderService == null) {
            return "";
        }

        Map<String, SkillMetadata> skills = skillLoaderService.getLoadedSkills();
        if (skills == null || skills.isEmpty()) {
            return "";
        }

        StringBuilder sb = new StringBuilder();
        sb.append("## 已激活的 Skills\n\n");
        sb.append("以下专业 Skills 已激活，可通过 `skill_{{skillId}}` 工具调用：\n\n");

        for (Map.Entry<String, SkillMetadata> entry : skills.entrySet()) {
            SkillMetadata metadata = entry.getValue();
            sb.append("- **").append(metadata.getName()).append("**");
            if (metadata.getDescription() != null) {
                sb.append(": ").append(metadata.getDescription());
            }
            sb.append("\n");
        }

        return sb.toString();
    }
}
