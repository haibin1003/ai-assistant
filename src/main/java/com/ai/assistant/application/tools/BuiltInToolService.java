package com.ai.assistant.application.tools;

import com.ai.assistant.application.context.dto.UserContextDTO;
import com.ai.assistant.application.tools.dto.GenerateDocumentRequest;
import com.ai.assistant.domain.entity.GeneratedDocument;
import com.ai.assistant.infrastructure.mcp.ToolRouter;
import com.ai.assistant.infrastructure.mcp.dto.ToolResult;
import com.ai.assistant.infrastructure.search.SearchClient;
import com.ai.assistant.infrastructure.search.SearchResult;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 内置工具服务
 * 注册并执行内置工具：web_search, browser_navigate, browser_snapshot
 */
@Slf4j
@Service
public class BuiltInToolService {

    private final ToolRouter toolRouter;
    private final List<SearchClient> searchClients;
    private final ObjectMapper objectMapper;
    private final DocumentGenerationService documentGenerationService;
    private com.ai.assistant.application.skill.SkillLoaderService skillLoaderService;

    public BuiltInToolService(ToolRouter toolRouter,
                              List<SearchClient> searchClients,
                              ObjectMapper objectMapper,
                              DocumentGenerationService documentGenerationService) {
        this.toolRouter = toolRouter;
        this.searchClients = searchClients;
        this.objectMapper = objectMapper;
        this.documentGenerationService = documentGenerationService;
    }

    // 注入 SkillLoaderService（Setter注入，避免循环依赖）
    @Autowired(required = false)
    public void setSkillLoaderService(com.ai.assistant.application.skill.SkillLoaderService skillLoaderService) {
        this.skillLoaderService = skillLoaderService;
        log.info("SkillLoaderService injected into BuiltInToolService");
    }

    @PostConstruct
    public void init() {
        // 注册 web_search 工具
        toolRouter.registerExecutor("web_search", this::executeWebSearch);

        // 注册浏览器工具
        toolRouter.registerExecutor("browser_navigate", this::executeBrowserNavigate);
        toolRouter.registerExecutor("browser_snapshot", this::executeBrowserSnapshot);

        // 注册文档生成工具
        toolRouter.registerExecutor("generate_excel", this::executeGenerateExcel);
        toolRouter.registerExecutor("generate_word", this::executeGenerateWord);

        log.info("Built-in tools registered: web_search, browser_navigate, browser_snapshot, generate_excel, generate_word");
    }

    /**
     * 注册 Skill 工具
     */
    public void registerSkillTool(String toolName, String skillName, String description,
                                     Map<String, Object> inputSchema, Object skillMetadata) {
        log.info("Registering skill tool: toolName={}, skillName={}", toolName, skillName);
        // 动态注册 skill 工具，使用统一的执行器
        toolRouter.registerExecutor(toolName, this::executeSkill);
    }

    /**
     * 执行 Skill 工具
     */
    private ToolResult executeSkill(Map<String, Object> arguments, UserContextDTO context) {
        // 获取工具名（传入的是原始工具名，可能是 skill_skill_xxx 格式）
        String toolName = (String) arguments.getOrDefault("_toolName", "unknown");
        String action = getString(arguments, "action");

        // 提取 skillId：
        // - toolName = skill_skill_xxx → skillId = skill-xxx（双重前缀，ToolRouter 已规范化）
        // - toolName = skill_xxx → skillId = skill-xxx（单前缀，直接提取）
        String skillId;
        if (toolName.startsWith("skill_skill_")) {
            // 双重前缀：skill_skill_0a5f541a → 提取 "0a5f541a" → 还原为 "skill-0a5f541a"
            String normalizedId = toolName.substring("skill_skill_".length());
            skillId = "skill-" + normalizedId.replace("_", "-");
        } else if (toolName.startsWith("skill_")) {
            // 单前缀：skill_0a5f541a → 提取 "0a5f541a" → 还原为 "skill-0a5f541a"
            String normalizedId = toolName.substring("skill_".length());
            skillId = "skill-" + normalizedId.replace("_", "-");
        } else {
            skillId = "unknown";
        }

        log.info("Executing skill: toolName={}, skillId={}, action={}", toolName, skillId, action);

        // 调用 SkillLoaderService 获取 metadata
        String skillName = "unknown";
        log.info("Checking skillLoaderService for skillId={}, skillLoaderService is {}", skillId, skillLoaderService != null ? "NOT NULL" : "NULL");
        if (skillLoaderService != null) {
            var metadata = skillLoaderService.getSkillMetadata(skillId);
            log.info("Metadata lookup result: {}", metadata);
            if (metadata != null) {
                skillName = metadata.getName();
                log.info("Skill metadata found: name={}, tools={}", metadata.getName(), metadata.getRequiredTools());
            }
        }

        // 返回 skill 执行结果
        return ToolResult.success(Map.of(
                "skillId", skillId,
                "skillName", skillName,
                "action", action != null ? action : "execute",
                "message", "Skill executed: " + skillName
        ));
    }

    /**
     * 执行网页搜索
     */
    private ToolResult executeWebSearch(Map<String, Object> arguments, UserContextDTO context) {
        String query = getString(arguments, "query");
        if (query == null || query.isEmpty()) {
            return ToolResult.failure("Query parameter is required");
        }

        log.info("Executing web search: {}", query);

        // 选择搜索客户端（优先 Serper）
        SearchClient client = searchClients.stream()
            .filter(c -> "serper".equals(c.getProviderName()))
            .findFirst()
            .orElse(null);

        if (client == null) {
            log.warn("No search client available, returning placeholder results");
            return getPlaceholderSearchResults(query);
        }

        SearchResult result = client.search(query);

        if (!result.isSuccess()) {
            return ToolResult.failure(result.getError());
        }

        // 格式化结果
        List<Map<String, String>> formattedResults = result.getItems().stream()
            .limit(10)
            .map(item -> {
                Map<String, String> map = new HashMap<>();
                map.put("title", item.getTitle());
                map.put("link", item.getLink());
                map.put("snippet", item.getSnippet());
                map.put("source", item.getSource());
                return map;
            })
            .collect(Collectors.toList());

        return ToolResult.success(Map.of(
            "query", query,
            "totalResults", formattedResults.size(),
            "items", formattedResults
        ));
    }

    /**
     * 执行浏览器导航（占位实现）
     */
    private ToolResult executeBrowserNavigate(Map<String, Object> arguments, UserContextDTO context) {
        String url = getString(arguments, "url");
        if (url == null || url.isEmpty()) {
            return ToolResult.failure("URL parameter is required");
        }

        log.info("Browser navigate requested: {}", url);

        // 占位实现 - 实际需要 Playwright 集成
        Map<String, Object> response = new HashMap<>();
        response.put("status", "placeholder");
        response.put("url", url);
        response.put("message", "Browser automation not yet implemented. URL: " + url);

        return ToolResult.success(response);
    }

    /**
     * 执行浏览器快照（占位实现）
     */
    private ToolResult executeBrowserSnapshot(Map<String, Object> arguments, UserContextDTO context) {
        log.info("Browser snapshot requested");

        // 占位实现
        Map<String, Object> response = new HashMap<>();
        response.put("status", "placeholder");
        response.put("message", "Browser automation not yet implemented");
        response.put("content", "This is a placeholder for browser snapshot functionality.");

        return ToolResult.success(response);
    }

    /**
     * 占位搜索结果
     */
    private ToolResult getPlaceholderSearchResults(String query) {
        return ToolResult.success(Map.of(
            "query", query,
            "totalResults", 1,
            "items", List.of(Map.of(
                "title", "Search service not configured",
                "link", "",
                "snippet", "Please configure Serper API key to enable web search.",
                "source", "system"
            ))
        ));
    }

    private String getString(Map<String, Object> map, String key) {
        Object value = map.get(key);
        return value != null ? value.toString() : null;
    }

    /**
     * 执行 Excel 生成
     */
    @SuppressWarnings("unchecked")
    private ToolResult executeGenerateExcel(Map<String, Object> arguments, UserContextDTO context) {
        log.info("Executing generate_excel");

        String title = getString(arguments, "title");
        String sheetName = getString(arguments, "sheet_name");

        if (title == null || title.isEmpty()) {
            return ToolResult.failure("title parameter is required");
        }

        // 解析 headers
        List<String> headers = null;
        Object headersObj = arguments.get("headers");
        if (headersObj instanceof List) {
            headers = ((List<Object>) headersObj).stream()
                .map(Object::toString)
                .collect(Collectors.toList());
        }

        // 解析 rows
        List<List<String>> rows = null;
        Object rowsObj = arguments.get("rows");
        if (rowsObj instanceof List) {
            rows = ((List<Object>) rowsObj).stream()
                .map(row -> {
                    if (row instanceof List) {
                        return ((List<Object>) row).stream()
                            .map(cell -> cell != null ? cell.toString() : "")
                            .collect(Collectors.toList());
                    }
                    return Collections.<String>emptyList();
                })
                .collect(Collectors.toList());
        }

        if (headers == null || headers.isEmpty()) {
            return ToolResult.failure("headers parameter is required");
        }

        if (rows == null || rows.isEmpty()) {
            return ToolResult.failure("rows parameter is required");
        }

        try {
            GenerateDocumentRequest request = GenerateDocumentRequest.builder()
                .title(title)
                .headers(headers)
                .rows(rows)
                .sheetName(sheetName)
                .documentType("EXCEL")
                .build();

            GeneratedDocument doc = documentGenerationService.generateExcel(request);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("documentId", doc.getDocumentId());
            result.put("title", doc.getTitle());
            result.put("fileName", doc.getFileName());
            result.put("fileSize", doc.getFileSize());
            result.put("downloadUrl", "/api/v1/documents/" + doc.getDocumentId() + "/download");
            result.put("message", "文档生成成功");

            return ToolResult.success(result);

        } catch (Exception e) {
            log.error("生成 Excel 失败: {}", e.getMessage(), e);
            return ToolResult.failure("生成 Excel 失败: " + e.getMessage());
        }
    }

    /**
     * 执行 Word 生成
     */
    @SuppressWarnings("unchecked")
    private ToolResult executeGenerateWord(Map<String, Object> arguments, UserContextDTO context) {
        log.info("Executing generate_word");

        String title = getString(arguments, "title");

        if (title == null || title.isEmpty()) {
            return ToolResult.failure("title parameter is required");
        }

        // 解析 headers
        List<String> headers = null;
        Object headersObj = arguments.get("headers");
        if (headersObj instanceof List) {
            headers = ((List<Object>) headersObj).stream()
                .map(Object::toString)
                .collect(Collectors.toList());
        }

        // 解析 rows
        List<List<String>> rows = null;
        Object rowsObj = arguments.get("rows");
        if (rowsObj instanceof List) {
            rows = ((List<Object>) rowsObj).stream()
                .map(row -> {
                    if (row instanceof List) {
                        return ((List<Object>) row).stream()
                            .map(cell -> cell != null ? cell.toString() : "")
                            .collect(Collectors.toList());
                    }
                    return Collections.<String>emptyList();
                })
                .collect(Collectors.toList());
        }

        if (headers == null || headers.isEmpty()) {
            return ToolResult.failure("headers parameter is required");
        }

        if (rows == null || rows.isEmpty()) {
            return ToolResult.failure("rows parameter is required");
        }

        try {
            GenerateDocumentRequest request = GenerateDocumentRequest.builder()
                .title(title)
                .headers(headers)
                .rows(rows)
                .documentType("WORD")
                .build();

            GeneratedDocument doc = documentGenerationService.generateWord(request);

            Map<String, Object> result = new HashMap<>();
            result.put("success", true);
            result.put("documentId", doc.getDocumentId());
            result.put("title", doc.getTitle());
            result.put("fileName", doc.getFileName());
            result.put("fileSize", doc.getFileSize());
            result.put("downloadUrl", "/api/v1/documents/" + doc.getDocumentId() + "/download");
            result.put("message", "文档生成成功");

            return ToolResult.success(result);

        } catch (Exception e) {
            log.error("生成 Word 失败: {}", e.getMessage(), e);
            return ToolResult.failure("生成 Word 失败: " + e.getMessage());
        }
    }
}
