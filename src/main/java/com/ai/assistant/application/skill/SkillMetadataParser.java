package com.ai.assistant.application.skill;

import com.ai.assistant.application.skill.dto.AgentMetadata;
import com.ai.assistant.application.skill.dto.SkillMetadata;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * SKILL.md 解析器
 * 解析标准格式的 SKILL.md 文件，提取元数据
 */
@Slf4j
@Component
public class SkillMetadataParser {

    // 匹配 ## Description 或 ## 描述
    private static final Pattern DESCRIPTION_PATTERN = Pattern.compile(
            "(?i)(?:##\\s*(?:Description|描述)\\s*\\n)(.*?)(?=##|\\Z)",
            Pattern.DOTALL
    );

    // 匹配 ## Trigger Keywords 或 ## 触发关键词
    private static final Pattern TRIGGER_PATTERN = Pattern.compile(
            "(?i)(?:##\\s*(?:Trigger\\s*Keywords|触发关键词)\\s*\\n)(.*?)(?=##|\\Z)",
            Pattern.DOTALL
    );

    // 匹配 ## Required Tools 或 ## 所需工具
    private static final Pattern TOOLS_PATTERN = Pattern.compile(
            "(?i)(?:##\\s*(?:Required\\s*Tools|所需工具)\\s*\\n)(.*?)(?=##|\\Z)",
            Pattern.DOTALL
    );

    // 匹配 ## Prompt 或 ## 提示词
    private static final Pattern PROMPT_PATTERN = Pattern.compile(
            "(?i)(?:##\\s*(?:Prompt|提示词模板)\\s*\\n)(.*?)(?=##|\\Z)",
            Pattern.DOTALL
    );

    /**
     * 解析 SKILL.md 内容
     */
    public SkillMetadata parse(String skillMdContent) {
        if (!StringUtils.hasText(skillMdContent)) {
            throw new IllegalArgumentException("SKILL.md content is empty");
        }

        SkillMetadata.SkillMetadataBuilder builder = SkillMetadata.builder();

        // 解析第一行作为名称（# Skill Name）
        Pattern namePattern = Pattern.compile("^#\\s+(.+)$", Pattern.MULTILINE);
        Matcher nameMatcher = namePattern.matcher(skillMdContent);
        if (nameMatcher.find()) {
            builder.name(nameMatcher.group(1).trim());
        }

        // 解析描述
        Matcher descMatcher = DESCRIPTION_PATTERN.matcher(skillMdContent);
        if (descMatcher.find()) {
            builder.description(cleanContent(descMatcher.group(1)));
        }

        // 解析触发关键词
        Matcher triggerMatcher = TRIGGER_PATTERN.matcher(skillMdContent);
        if (triggerMatcher.find()) {
            builder.triggerKeywords(parseList(triggerMatcher.group(1)));
        }

        // 解析所需工具
        Matcher toolsMatcher = TOOLS_PATTERN.matcher(skillMdContent);
        if (toolsMatcher.find()) {
            builder.requiredTools(parseList(toolsMatcher.group(1)));
        }

        // 解析提示词模板
        Matcher promptMatcher = PROMPT_PATTERN.matcher(skillMdContent);
        if (promptMatcher.find()) {
            builder.promptTemplate(cleanContent(promptMatcher.group(1)));
        }

        SkillMetadata metadata = builder.build();

        // 如果没有名称，尝试使用内容的第一行
        if (!StringUtils.hasText(metadata.getName())) {
            String firstLine = skillMdContent.lines().findFirst().orElse("");
            if (firstLine.startsWith("# ")) {
                metadata.setName(firstLine.substring(2).trim());
            }
        }

        log.debug("Parsed skill metadata: name={}, triggers={}, tools={}",
                metadata.getName(),
                metadata.getTriggerKeywords(),
                metadata.getRequiredTools());

        return metadata;
    }

    /**
     * 解析列表（- item 或 1. item 格式）
     */
    private List<String> parseList(String content) {
        if (content == null || content.isEmpty()) {
            return new ArrayList<>();
        }

        List<String> result = new ArrayList<>();

        // 匹配 - item 格式
        Pattern dashPattern = Pattern.compile("^-\\s+(.+)$", Pattern.MULTILINE);
        Matcher dashMatcher = dashPattern.matcher(content);
        while (dashMatcher.find()) {
            result.add(dashMatcher.group(1).trim());
        }

        // 如果没匹配到，尝试 1. item 格式
        if (result.isEmpty()) {
            Pattern numPattern = Pattern.compile("^\\d+\\.\\s+(.+)$", Pattern.MULTILINE);
            Matcher numMatcher = numPattern.matcher(content);
            while (numMatcher.find()) {
                result.add(numMatcher.group(1).trim());
            }
        }

        // 如果还是没有，尝试按行分割
        if (result.isEmpty()) {
            result = Arrays.stream(content.split("\\n"))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .collect(Collectors.toList());
        }

        return result;
    }

    /**
     * 清理内容，移除多余空白
     */
    private String cleanContent(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }
        return content.trim()
                .replaceAll("\\n{3,}", "\n\n")
                .replaceAll("^\\s+", "")
                .replaceAll("\\s+$", "");
    }

    /**
     * 生成示例 SKILL.md 内容
     */
    public static String generateSampleSkillMd() {
        return "# Software Report Generator\n\n" +
                "## Description\n" +
                "Generate software usage reports based on system data. This skill helps users create Excel/Word reports about software usage across business systems.\n\n" +
                "## Trigger Keywords\n" +
                "- 生成报表\n" +
                "- 软件使用情况\n" +
                "- 报表下载\n" +
                "- 生成报告\n\n" +
                "## Required Tools\n" +
                "- osrm_list_software\n" +
                "- osrm_list_business_systems\n" +
                "- generate_excel\n\n" +
                "## Prompt\n" +
                "You are a software report assistant. When user asks to generate a report:\n" +
                "1. First query all software packages using osrm_list_software\n" +
                "2. Query business systems using osrm_list_business_systems\n" +
                "3. Generate an Excel report with the queried data\n" +
                "4. Return the download link to the user\n";
    }
}