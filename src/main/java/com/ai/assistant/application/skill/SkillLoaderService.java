package com.ai.assistant.application.skill;

import com.ai.assistant.application.skill.dto.SkillMetadata;
import com.ai.assistant.application.tools.DocumentGenerationService;
import com.ai.assistant.domain.entity.SkillPackage;
import com.ai.assistant.infrastructure.mcp.ToolRouter;
import com.ai.assistant.infrastructure.mcp.dto.ToolDefinition;
import com.ai.assistant.infrastructure.mcp.dto.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Skill 加载服务 - 动态加载 Skills 到 AI 助手
 * 将 Skills 转换为工具定义并注册到 ToolRouter
 */
@Slf4j
@Service
public class SkillLoaderService {

    private final SkillPackageService skillPackageService;
    private final ToolRouter toolRouter;
    private DocumentGenerationService documentGenerationService;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Value("${skill.storage.path:./skills-storage}")
    private String storageBasePath;

    @Value("${document.storage.path:${user.dir}/documents}")
    private String documentsPath;

    @Autowired
    public SkillLoaderService(SkillPackageService skillPackageService, ToolRouter toolRouter) {
        this.skillPackageService = skillPackageService;
        this.toolRouter = toolRouter;
    }

    @Autowired(required = false)
    public void setDocumentGenerationService(DocumentGenerationService documentGenerationService) {
        this.documentGenerationService = documentGenerationService;
    }

    // 缓存已加载的 Skills 元数据
    private final Map<String, SkillMetadata> loadedSkills = new ConcurrentHashMap<>();

    /**
     * 从 MCP 响应结果中提取 text 字段的 JSON 数据
     */
    private String extractTextFromMcpResult(ToolResult result) {
        if (result == null || result.getData() == null) {
            return "{}";
        }
        try {
            var data = result.getData();
            if (data.has("content") && data.get("content").isArray()) {
                var content = data.get("content");
                for (var item : content) {
                    if (item.has("text")) {
                        String text = item.get("text").asText();
                        // 尝试解析内部 JSON
                        try {
                            var mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                            var inner = mapper.readTree(text);
                            // 如果是数组格式 {"data": [...]} 或 {"items": [...]}
                            if (inner.has("data")) {
                                return mapper.writeValueAsString(inner.get("data"));
                            } else if (inner.has("items")) {
                                return mapper.writeValueAsString(inner.get("items"));
                            } else if (inner.has("packages")) {
                                return mapper.writeValueAsString(inner.get("packages"));
                            } else if (inner.has("subscriptions")) {
                                return mapper.writeValueAsString(inner.get("subscriptions"));
                            }
                            // 如果不是标准格式，返回原文本
                            return text;
                        } catch (Exception e) {
                            return text;
                        }
                    }
                }
            }
            // 如果没有 content，直接返回 toString
            return data.toString();
        } catch (Exception e) {
            log.warn("Failed to extract text from MCP result: {}", e.getMessage());
            return "{}";
        }
    }

    /**
     * 内部执行脚本（供 autoScript 机制调用）
     * @return 脚本输出内容，如果失败返回 null
     */
    private String executeScriptInternal(String skillId, String scriptPath, String params) {
        try {
            // 获取 Skill 的存储路径
            SkillPackage skill = skillPackageService.getSkillById(skillId);

            // 读取脚本文件
            byte[] scriptContent = skillPackageService.getFileContent(skill.getStoragePath(), "scripts/" + scriptPath);
            if (scriptContent == null) {
                log.error("Script file not found: scripts/{}", scriptPath);
                return null;
            }

            // 创建临时文件执行
            Path tempDir = Files.createTempDirectory("skill-scripts");
            Path scriptFile = tempDir.resolve(scriptPath);
            Files.write(scriptFile, scriptContent);

            // 确定解释器
            String interpreter = "python";
            if (scriptPath.endsWith(".sh")) {
                interpreter = "bash";
            } else if (scriptPath.endsWith(".js")) {
                interpreter = "node";
            }

            // 确保 documents 目录存在
            Files.createDirectories(Paths.get(documentsPath));

            // 解析 params，提取 output 文件名，并替换为 documents 目录的绝对路径
            String outputFileName = "output.md";
            String[] parts = params.split("\\s+");
            for (int i = 0; i < parts.length - 1; i++) {
                if ("--output".equals(parts[i])) {
                    outputFileName = parts[i + 1];
                    break;
                }
            }

            // 如果文件名没有扩展名，添加 .md
            if (!outputFileName.contains(".")) {
                outputFileName += ".md";
            }

            String finalOutputPath = Paths.get(documentsPath, outputFileName).toString();
            // 构建新的 params，用绝对路径替换原文件名
            String finalParams = params.replace("--output " + outputFileName, "--output " + finalOutputPath);
            // 如果原参数没有 --output，添加上去
            if (!finalParams.contains("--output")) {
                finalParams = finalParams + " --output " + finalOutputPath;
            }

            log.info("Executing script with output: {}", finalOutputPath);

            // 执行脚本
            ProcessBuilder pb = new ProcessBuilder(interpreter, scriptFile.toString());
            pb.environment().put("SCRIPT_PARAMS", finalParams);
            pb.environment().put("SKILL_ID", skillId);
            pb.redirectErrorStream(true);

            Process process = pb.start();
            StringBuilder output = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    output.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();

            // 清理临时文件
            Files.deleteIfExists(scriptFile);
            Files.deleteIfExists(tempDir);

            if (exitCode == 0) {
                log.info("Script executed successfully: {}", scriptPath);
                return output.toString();
            } else {
                log.error("Script execution failed with exit code: {}. Output: {}", exitCode, output);
                return null;
            }
        } catch (Exception e) {
            log.error("Failed to execute script internally: {}", scriptPath, e);
            return null;
        }
    }

    /**
     * 启动时加载所有已发布的 Skills 并注册脚本执行工具
     */
    @PostConstruct
    public void loadAllSkills() {
        log.info("Starting to load all published skills...");
        try {
            // 注册脚本执行工具
            registerScriptExecutor();

            List<SkillPackage> skills = skillPackageService.getPublishedSkills();
            for (SkillPackage skillPackage : skills) {
                try {
                    loadSkill(skillPackage);
                } catch (Exception e) {
                    log.error("Failed to load skill: {}", skillPackage.getSkillId(), e);
                }
            }
            log.info("Loaded {} skills successfully", loadedSkills.size());
        } catch (Exception e) {
            log.error("Failed to load skills on startup", e);
        }
    }

    /**
     * 注册脚本执行工具
     * 允许 AI 执行 skills/scripts/ 目录下的脚本
     */
    private void registerScriptExecutor() {
        toolRouter.registerExecutor("execute_script", (arguments, context) -> {
            String scriptPath = (String) arguments.getOrDefault("script_path", "");
            String skillId = (String) arguments.getOrDefault("skill_id", "");
            String params = (String) arguments.getOrDefault("params", "");

            log.info("Executing script: skillId={}, script={}, params={}", skillId, scriptPath, params);

            if (scriptPath.isEmpty() || skillId.isEmpty()) {
                return ToolResult.failure("Missing required parameters: skill_id and script_path");
            }

            try {
                // 获取 Skill 的存储路径
                SkillPackage skill = skillPackageService.getSkillById(skillId);

                // 读取脚本文件
                byte[] scriptContent = skillPackageService.getFileContent(skill.getStoragePath(), "scripts/" + scriptPath);
                if (scriptContent == null) {
                    return ToolResult.failure("Script file not found: " + scriptPath);
                }

                // 确保 documents 目录存在
                Path documentsDir = Paths.get(documentsPath);
                Files.createDirectories(documentsDir);

                // 创建临时文件执行
                Path tempDir = Files.createTempDirectory("skill-scripts");
                Path scriptFile = tempDir.resolve(scriptPath);
                Files.write(scriptFile, scriptContent);

                // 解析 params 中的 --output 参数
                String outputFileName = null;
                String dataFilePath = null;
                String[] paramParts = params.split("\\s+");
                for (int i = 0; i < paramParts.length; i++) {
                    if ("--output".equals(paramParts[i]) && i + 1 < paramParts.length) {
                        outputFileName = paramParts[i + 1];
                    } else if ("--data-file".equals(paramParts[i]) && i + 1 < paramParts.length) {
                        dataFilePath = paramParts[i + 1];
                    }
                }

                // 确定解释器
                String interpreter = "python";
                if (scriptPath.endsWith(".sh")) {
                    interpreter = "bash";
                } else if (scriptPath.endsWith(".js")) {
                    interpreter = "node";
                }

                // 构建命令
                String fullParams = params;
                // 如果没有指定 --output，使用 documents 目录
                String finalOutputPath = null;
                if (outputFileName != null && !outputFileName.startsWith("/") && !outputFileName.contains(":")) {
                    finalOutputPath = Paths.get(documentsPath, outputFileName).toString();
                    fullParams = params.replace("--output " + outputFileName, "--output " + finalOutputPath);
                }

                // 执行脚本
                ProcessBuilder pb = new ProcessBuilder(interpreter, scriptFile.toString());
                pb.environment().put("SCRIPT_PARAMS", fullParams);
                pb.environment().put("SKILL_ID", skillId);
                pb.redirectErrorStream(true);

                Process process = pb.start();
                StringBuilder output = new StringBuilder();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        output.append(line).append("\n");
                    }
                }

                int exitCode = process.waitFor();

                // 清理临时文件
                Files.deleteIfExists(scriptFile);
                Files.deleteIfExists(tempDir);

                if (exitCode == 0) {
                    // 如果有输出文件，保存文档记录
                    String documentId = null;
                    String downloadUrl = null;
                    long fileSize = 0;
                    if (finalOutputPath != null && Files.exists(Paths.get(finalOutputPath))) {
                        fileSize = Files.size(Paths.get(finalOutputPath));
                        documentId = java.util.UUID.randomUUID().toString();
                        // 根据文件扩展名确定文档类型
                        String fileName = Paths.get(finalOutputPath).getFileName().toString().toLowerCase();
                        com.ai.assistant.domain.entity.GeneratedDocument.DocumentType docType;
                        if (fileName.endsWith(".md")) {
                            docType = com.ai.assistant.domain.entity.GeneratedDocument.DocumentType.MARKDOWN;
                        } else if (fileName.endsWith(".docx") || fileName.endsWith(".doc")) {
                            docType = com.ai.assistant.domain.entity.GeneratedDocument.DocumentType.WORD;
                        } else {
                            docType = com.ai.assistant.domain.entity.GeneratedDocument.DocumentType.EXCEL;
                        }
                        try {
                            if (documentGenerationService != null) {
                                var savedDoc = documentGenerationService.saveExistingFile(
                                    documentId,
                                    "Generated Report - " + scriptPath,
                                    Paths.get(finalOutputPath).getFileName().toString(),
                                    finalOutputPath,
                                    fileSize,
                                    docType
                                );
                                documentId = savedDoc.getDocumentId();
                                // 使用 documentGenerationService 获取下载 URL（支持 MinIO）
                                downloadUrl = documentGenerationService.getDownloadUrl(savedDoc);
                            }
                        } catch (Exception docEx) {
                            log.warn("Failed to save document record: {}", docEx.getMessage());
                        }
                    }

                    Map<String, Object> result = new java.util.HashMap<>();
                    result.put("script", scriptPath);
                    result.put("skillId", skillId);
                    result.put("output", output.toString());
                    result.put("exitCode", exitCode);
                    if (documentId != null) {
                        result.put("documentId", documentId);
                        result.put("downloadUrl", downloadUrl);
                        result.put("fileSize", fileSize);
                    }
                    return ToolResult.success(result);
                } else {
                    return ToolResult.failure("Script execution failed with exit code: " + exitCode + "\n" + output);
                }
            } catch (Exception e) {
                log.error("Failed to execute script: {}", scriptPath, e);
                return ToolResult.failure("Failed to execute script: " + e.getMessage());
            }
        });

        // 注册读取 Reference 文档的工具
        toolRouter.registerExecutor("read_reference", (arguments, context) -> {
            String filePath = (String) arguments.getOrDefault("file_path", "");
            String skillId = (String) arguments.getOrDefault("skill_id", "");

            log.info("Reading reference: skillId={}, file={}", skillId, filePath);

            if (filePath.isEmpty() || skillId.isEmpty()) {
                return ToolResult.failure("Missing required parameters: skill_id and file_path");
            }

            try {
                SkillPackage skill = skillPackageService.getSkillById(skillId);
                String content = skillPackageService.getFileContentByPath(skill.getStoragePath(), "references/" + filePath);

                if (content == null) {
                    return ToolResult.failure("Reference file not found: " + filePath);
                }

                return ToolResult.success(Map.of(
                        "file", filePath,
                        "skillId", skillId,
                        "content", content,
                        "size", content.length()
                ));
            } catch (Exception e) {
                log.error("Failed to read reference: {}", filePath, e);
                return ToolResult.failure("Failed to read reference: " + e.getMessage());
            }
        });

        log.info("Registered script executor and reference reader tools");
    }

    /**
     * 加载单个 Skill
     */
    public void loadSkill(SkillPackage skillPackage) {
        String skillId = skillPackage.getSkillId();
        log.info("Loading skill: skillId={}, name={}", skillId, skillPackage.getName());

        try {
            // 从 MinIO 获取 SKILL.md 内容
            String mdContent = skillPackageService.getSkillMdContent(skillPackage);
            if (mdContent == null) {
                throw new RuntimeException("SKILL.md not found");
            }

            // 解析 SKILL.md
            SkillMetadataParser parser = new SkillMetadataParser();
            SkillMetadata metadata = parser.parse(mdContent);

            // 使用 skillPackage 的名称覆盖（用户定义的显示名称）
            metadata.setName(skillPackage.getName());

            // 缓存元数据
            loadedSkills.put(skillId, metadata);

            // 注册为工具
            registerSkillAsTool(skillId, skillPackage.getName(), metadata);

            log.info("Loaded skill: skillId={}, name={}", skillId, skillPackage.getName());
        } catch (Exception e) {
            log.error("Failed to load skill: {}", skillId, e);
            throw e;
        }
    }

    /**
     * 将 Skill 注册为工具
     */
    private void registerSkillAsTool(String skillId, String skillName, SkillMetadata metadata) {
        // 构建工具名：将 skillId 中的连字符替换为下划线
        // 例如：skill-0a5f541a → skill_0a5f541a
        String toolName = skillId.replace("-", "_");

        // 保存 metadata 引用供执行器使用
        SkillMetadata skillMetadata = metadata;
        String currentSkillName = skillName;
        String currentSkillId = skillId;

        // 直接注册到 ToolRouter，使用 skill_ 前缀以被识别为内置工具
        toolRouter.registerExecutor(toolName, (arguments, context) -> {
            String action = (String) arguments.getOrDefault("action", "execute");
            // 获取用户查询参数（keyword 或 query）
            String keyword = (String) arguments.getOrDefault("keyword",
                                arguments.getOrDefault("query", ""));
            log.info("Executing skill: skillId={}, skillName={}, action={}, keyword={}",
                    currentSkillId, currentSkillName, action, keyword);

            try {
                // 根据 skill 类型调用对应的 OSRM 工具
                if ("Open Source Reviewer".equals(currentSkillName) || "License Compliance Checker".equals(currentSkillName)) {
                    // 调用 OSRM portal 工具搜索软件信息
                    // 如果没有 keyword，获取所有软件进行审查
                    Map<String, Object> searchArgs = new java.util.HashMap<>();
                    searchArgs.put("size", 10);
                    if (keyword != null && !keyword.isEmpty()) {
                        searchArgs.put("keyword", keyword);
                    }
                    var searchResult = toolRouter.execute("search_software", searchArgs, context);
                    var detailResult = toolRouter.execute("get_portal_stats", Map.of(), context);

                    // 如果有关键词，也调用 web_search 获取网络信息
                    var webSearchResult = keyword != null && !keyword.isEmpty()
                        ? toolRouter.execute("web_search", Map.of("query", keyword), context)
                        : null;

                    return com.ai.assistant.infrastructure.mcp.dto.ToolResult.success(Map.of(
                            "skillId", currentSkillId,
                            "skillName", currentSkillName,
                            "action", action,
                            "keyword", keyword != null ? keyword : "all",
                            "message", "Skill " + currentSkillName + " executed successfully",
                            "searchResult", searchResult.getData() != null ? searchResult.getData().toString() : "No data",
                            "portalStats", detailResult.getData() != null ? detailResult.getData().toString() : "No data",
                            "webSearchResult", webSearchResult != null && webSearchResult.isSuccess()
                                ? webSearchResult.getData().toString()
                                : "No web search performed",
                            "hint", "Based on the search results and portal stats, provide a comprehensive review"
                    ));
                }

                // Software Operations Weekly Report Skill
                // 使用已有工具组合生成周报：list_all_packages + list_pending_approvals + list_my_subscriptions + generate_excel/word
                if ("Software Operations Weekly Report".equals(currentSkillName)) {
                    log.info("Executing weekly report skill: skillId={}", currentSkillId);

                    // 确定日期范围
                    String startDate = (String) arguments.getOrDefault("start_date",
                                        arguments.getOrDefault("startDate", ""));
                    String endDate = (String) arguments.getOrDefault("end_date",
                                        arguments.getOrDefault("endDate", ""));

                    // 如果没有提供时间范围，使用当前周（周一到周日）
                    if (startDate == null || startDate.isEmpty() || endDate == null || endDate.isEmpty()) {
                        LocalDate today = LocalDate.now();
                        int daysFromMonday = today.getDayOfWeek().getValue() - 1;
                        LocalDate monday = today.minusDays(daysFromMonday);
                        LocalDate sunday = monday.plusDays(6);
                        startDate = monday.toString();
                        endDate = sunday.toString();
                    }

                    // 步骤1：获取所有软件包列表
                    var packagesResult = toolRouter.execute("osrm_list_all_packages",
                            Map.of("size", 100), context);

                    // 步骤2：获取待审批列表
                    var pendingResult = toolRouter.execute("osrm_list_pending_approvals",
                            Map.of("size", 50), context);

                    // 步骤3：获取订阅记录
                    var subscriptionsResult = toolRouter.execute("osrm_list_my_subscriptions",
                            Map.of("size", 50), context);

                    // 步骤4：获取业务系统列表
                    var systemsResult = toolRouter.execute("osrm_list_business_systems",
                            Map.of(), context);

                    // 解析并组合数据（从 MCP content 中提取 text 字段）
                    String packagesData = extractTextFromMcpResult(packagesResult);
                    String pendingData = extractTextFromMcpResult(pendingResult);
                    String subscriptionsData = extractTextFromMcpResult(subscriptionsResult);
                    String systemsData = extractTextFromMcpResult(systemsResult);

                    // 生成周报内容摘要
                    String reportTitle = "软件运营周报 " + startDate + " 至 " + endDate;

                    // 构建脚本参数字符串
                    String scriptParams = String.format(
                            "--packages %s --pending %s --subscriptions %s --systems %s --output %s",
                            packagesData,
                            pendingData,
                            subscriptionsData,
                            systemsData,
                            "weekly_report.md"
                    );

                    // 使用 HashMap 避免 Map.of() 的 10 元素限制
                    java.util.Map<String, Object> resultMap = new java.util.HashMap<>();
                    resultMap.put("skillId", currentSkillId);
                    resultMap.put("skillName", currentSkillName);
                    resultMap.put("action", action);
                    resultMap.put("status", "generated");
                    resultMap.put("reportTitle", reportTitle);
                    resultMap.put("reportPeriod", startDate + " 至 " + endDate);

                    // 自动执行脚本
                    String scriptOutput = executeScriptInternal(currentSkillId, "generate_report.py", scriptParams);
                    if (scriptOutput != null) {
                        // 提取 JSON 结果
                        int jsonStart = scriptOutput.indexOf("{");
                        int jsonEnd = scriptOutput.lastIndexOf("}");
                        if (jsonStart >= 0 && jsonEnd > jsonStart) {
                            String jsonStr = scriptOutput.substring(jsonStart, jsonEnd + 1);
                            try {
                                com.fasterxml.jackson.databind.ObjectMapper mapper = new com.fasterxml.jackson.databind.ObjectMapper();
                                java.util.Map<String, Object> scriptResult = mapper.readValue(jsonStr, java.util.Map.class);
                                resultMap.put("status", "success");

                                // 获取生成的文件路径
                                String filePath = (String) scriptResult.get("filePath");
                                if (filePath == null) {
                                    // 默认路径
                                    filePath = Paths.get(documentsPath, "weekly_report.md").toString();
                                }

                                // 保存文档记录
                                if (Files.exists(Paths.get(filePath))) {
                                    long fileSize = Files.size(Paths.get(filePath));
                                    String documentId = java.util.UUID.randomUUID().toString();
                                    String downloadUrl = null;

                                    if (documentGenerationService != null) {
                                        try {
                                            var savedDoc = documentGenerationService.saveExistingFile(
                                                documentId,
                                                reportTitle,
                                                Paths.get(filePath).getFileName().toString(),
                                                filePath,
                                                fileSize,
                                                com.ai.assistant.domain.entity.GeneratedDocument.DocumentType.MARKDOWN
                                            );
                                            documentId = savedDoc.getDocumentId();
                                            // 使用 documentGenerationService 获取下载 URL（支持 MinIO）
                                            downloadUrl = documentGenerationService.getDownloadUrl(savedDoc);
                                            resultMap.put("documentId", documentId);
                                            resultMap.put("downloadUrl", downloadUrl);
                                        } catch (Exception docEx) {
                                            log.warn("Failed to save document record: {}", docEx.getMessage());
                                        }
                                    }

                                    // 读取 markdown 内容
                                    String markdownContent = Files.readString(Paths.get(filePath));
                                    resultMap.put("filePath", filePath);
                                    resultMap.put("fileSize", fileSize);
                                    resultMap.put("markdown", markdownContent);
                                    resultMap.put("message", markdownContent + "\n\n---\n**下载链接**：[点击下载 Markdown 文件](" + downloadUrl + ")");
                                } else {
                                    resultMap.put("message", reportTitle + "\n\n脚本执行成功，但文件未生成");
                                }
                            } catch (Exception ex) {
                                log.warn("Failed to parse script result JSON: {}", ex.getMessage());
                                resultMap.put("message", reportTitle + "\n\n" + scriptOutput);
                            }
                        } else {
                            resultMap.put("message", reportTitle + "\n\n" + scriptOutput);
                        }
                    } else {
                        resultMap.put("status", "failed");
                        resultMap.put("message", "脚本执行失败");
                    }

                    return ToolResult.success(resultMap);
                }

                // 默认返回
                return com.ai.assistant.infrastructure.mcp.dto.ToolResult.success(Map.of(
                        "skillId", currentSkillId,
                        "skillName", currentSkillName,
                        "action", action,
                        "keyword", keyword != null ? keyword : "",
                        "message", "Skill executed: " + currentSkillName
                ));
            } catch (Exception e) {
                log.error("Skill execution failed: {}", currentSkillId, e);
                return com.ai.assistant.infrastructure.mcp.dto.ToolResult.failure("Skill execution failed: " + e.getMessage());
            }
        });

        log.debug("Registered skill as tool: toolName={}", toolName);
    }

    /**
     * 重新加载 Skill
     */
    public void reloadSkill(String skillId) {
        log.info("Reloading skill: skillId={}", skillId);

        // 移除旧版本
        unloadSkill(skillId);

        // 加载新版本
        SkillPackage skillPackage = skillPackageService.getSkillById(skillId);
        loadSkill(skillPackage);
    }

    /**
     * 卸载 Skill
     */
    public void unloadSkill(String skillId) {
        log.info("Unloading skill: skillId={}", skillId);

        // 从缓存移除
        loadedSkills.remove(skillId);

        // TODO: 从 ToolRouter 移除工具注册
        // 这需要 BuiltInToolService 支持动态移除工具

        log.info("Unloaded skill: skillId={}", skillId);
    }

    /**
     * 获取所有已加载的 Skills
     */
    public Map<String, SkillMetadata> getLoadedSkills() {
        return Collections.unmodifiableMap(loadedSkills);
    }

    /**
     * 获取单个 Skill 的元数据
     */
    public SkillMetadata getSkillMetadata(String skillId) {
        return loadedSkills.get(skillId);
    }

    /**
     * 获取所有可用工具定义（包含动态加载的 Skills）
     */
    public List<Map<String, Object>> getSkillToolDefinitions() {
        List<Map<String, Object>> tools = new ArrayList<>();

        for (Map.Entry<String, SkillMetadata> entry : loadedSkills.entrySet()) {
            String skillId = entry.getKey();
            SkillMetadata metadata = entry.getValue();

            Map<String, Object> tool = new HashMap<>();
            tool.put("name", "skill_" + skillId);
            tool.put("description", metadata.getDescription());
            tool.put("skillId", skillId);
            tool.put("skillName", metadata.getName());
            tool.put("triggerKeywords", metadata.getTriggerKeywords());
            tool.put("requiredTools", metadata.getRequiredTools());

            tools.add(tool);
        }

        return tools;
    }

    /**
     * 获取 Skills 的工具定义列表（用于 LLM 工具调用）
     * 返回符合 ToolDefinition 格式的列表
     */
    public List<ToolDefinition> getSkillToolDefinitionsAsToolDefinitions() {
        List<ToolDefinition> tools = new ArrayList<>();

        for (Map.Entry<String, SkillMetadata> entry : loadedSkills.entrySet()) {
            String skillId = entry.getKey();
            SkillMetadata metadata = entry.getValue();

            JsonNode inputSchema = buildSkillInputSchema(metadata);

            // 工具名：直接使用规范化后的 skillId（skill_0a5f541a），不带额外前缀
            // registerSkillAsTool 会添加 skill_ 前缀
            String normalizedSkillId = skillId.replace("-", "_");
            tools.add(ToolDefinition.builder()
                    .name(normalizedSkillId)  // 如 skill_0a5f541a
                    .description(metadata.getDescription() != null ? metadata.getDescription() : "Skill: " + metadata.getName())
                    .inputSchema(inputSchema)
                    .build());
        }

        log.debug("Returning {} skill tools as ToolDefinition", tools.size());
        return tools;
    }

    /**
     * 构建 Skill 的输入 schema
     */
    private JsonNode buildSkillInputSchema(SkillMetadata metadata) {
        ObjectNode schema = objectMapper.createObjectNode();
        schema.put("type", "object");

        ObjectNode properties = objectMapper.createObjectNode();

        // action 参数
        ObjectNode actionProp = objectMapper.createObjectNode();
        actionProp.put("type", "string");
        actionProp.put("description", "要执行的行动，如 execute、query 等");
        properties.set("action", actionProp);

        // keyword/query 参数
        ObjectNode keywordProp = objectMapper.createObjectNode();
        keywordProp.put("type", "string");
        keywordProp.put("description", "搜索关键词或查询条件");
        properties.set("keyword", keywordProp);
        properties.set("query", keywordProp);

        // start_date/end_date 参数（周报用）
        ObjectNode startDateProp = objectMapper.createObjectNode();
        startDateProp.put("type", "string");
        startDateProp.put("description", "报告开始日期 (YYYY-MM-DD)");
        properties.set("start_date", startDateProp);
        properties.set("startDate", startDateProp);

        ObjectNode endDateProp = objectMapper.createObjectNode();
        endDateProp.put("type", "string");
        endDateProp.put("description", "报告结束日期 (YYYY-MM-DD)");
        properties.set("end_date", endDateProp);
        properties.set("endDate", endDateProp);

        schema.set("properties", properties);

        return schema;
    }
}
