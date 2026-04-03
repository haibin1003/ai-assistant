# MCP 工具路由模块详细设计

## 文档信息

- **模块名称**: MCP 工具路由 (MCP Tool Router)
- **版本**: 1.0.0
- **创建日期**: 2026-03-25
- **文档类型**: 模块详细设计文档

---

## 1. 模块概述

### 1.1 功能描述

MCP 工具路由模块负责：

- 解析工具名前缀，确定目标系统
- 注入用户认证令牌到 MCP 请求
- 调用目标系统的 MCP 网关
- 处理工具调用结果
- 内置工具注册与执行

### 1.2 模块位置

```
com.osrm.ai
├── infrastructure.mcp       # 基础设施层
│   ├── MCPClient.java
│   ├── ToolRouter.java
│   ├── BuiltInToolRegistry.java
│   └── dto/
│       ├── MCPRequest.java
│       ├── MCPResponse.java
│       └── ToolDefinition.java
├── infrastructure.search    # 搜索工具
│   ├── SearchProvider.java
│   ├── SerperClient.java
│   └── TavilyClient.java
└── infrastructure.browser   # 浏览器工具
    └── BrowserTool.java
```

---

## 2. 工具名解析规则

### 2.1 命名规范

工具名格式：`{toolPrefix}{toolName}`

| 工具名示例 | 前缀 | 系统ID | 实际工具名 |
|-----------|------|--------|-----------|
| `osrm_search_software` | `osrm_` | osrm | `search_software` |
| `osrm_create_package` | `osrm_` | osrm | `create_package` |
| `systemb_create_order` | `systemb_` | systemb | `create_order` |
| `web_search` | 无 | built-in | `web_search` |

### 2.2 解析流程

```
┌─────────────────────────────────────────────────────────────────────┐
│                        工具调用请求                                   │
│   toolName: "osrm_search_software"                                  │
│   arguments: { "keyword": "nginx" }                                  │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     1. 解析工具名前缀                                  │
│   parts = toolName.split("_", 2)                                    │
│   prefix = parts[0] + "_"                                           │
│   actualName = parts[1]                                             │
│                                                                      │
│   结果: prefix="osrm_", actualName="search_software"                 │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     2. 查找目标系统                                    │
│   system = systemRepository.findByToolPrefix("osrm_")               │
│                                                                      │
│   结果: system = { systemId: "osrm", mcpGatewayUrl: "..." }         │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     3. 创建 MCP 客户端                                 │
│   client = new MCPClient(system.mcpGatewayUrl)                      │
│   client.setAuthToken(context.accessToken)                           │
└──────────────────────────┬──────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     4. 调用工具                                        │
│   response = client.callTool("search_software", { keyword: "nginx" })│
└──────────────────────────┬──────────────────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────────────────┐
│                     5. 返回结果                                        │
│   return ToolResult.success(response.result)                         │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 3. 服务设计

### 3.1 ToolRouter

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ToolRouter {

    private final RegisteredSystemRepository systemRepository;
    private final BuiltInToolRegistry builtInToolRegistry;
    private final ObjectMapper objectMapper;

    // 内置工具前缀集合
    private static final Set<String> BUILTIN_PREFIXES = Set.of("web_", "browser_");

    /**
     * 执行工具调用
     *
     * @param toolName 工具名（带前缀）
     * @param arguments 参数
     * @param context 用户上下文
     * @return 工具执行结果
     */
    public ToolResult execute(String toolName, Map<String, Object> arguments,
                               UserContextDTO context) {
        log.info("Executing tool: {} with args: {}", toolName, arguments);

        try {
            // 检查是否为内置工具
            if (isBuiltInTool(toolName)) {
                return executeBuiltInTool(toolName, arguments, context);
            }

            // 解析工具名前缀
            ToolNameInfo info = parseToolName(toolName);

            // 查找目标系统
            RegisteredSystem system = systemRepository
                .findByToolPrefixAndIsActiveTrue(info.getPrefix())
                .orElseThrow(() -> new ToolNotFoundException(
                    "System not found for prefix: " + info.getPrefix()));

            // 创建 MCP 客户端
            MCPClient client = createMCPClient(system, context);

            // 调用工具
            MCPResponse response = client.callTool(info.getActualName(), arguments);

            if (response.isSuccess()) {
                return ToolResult.success(response.getResult());
            } else {
                return ToolResult.failure(response.getErrorMessage());
            }

        } catch (ToolNotFoundException e) {
            log.warn("Tool not found: {}", toolName);
            return ToolResult.failure("Tool not found: " + toolName);
        } catch (Exception e) {
            log.error("Tool execution failed: {}", toolName, e);
            return ToolResult.failure("Tool execution failed: " + e.getMessage());
        }
    }

    /**
     * 获取所有可用工具
     */
    public List<ToolDefinition> getAllAvailableTools(UserContextDTO context) {
        List<ToolDefinition> tools = new ArrayList<>();

        // 添加内置工具
        tools.addAll(builtInToolRegistry.getAllTools());

        // 添加目标系统工具
        if (context != null && context.getSystemId() != null) {
            systemRepository.findBySystemId(context.getSystemId())
                .ifPresent(system -> {
                    tools.addAll(getSystemTools(system));
                });
        }

        return tools;
    }

    /**
     * 检查是否为内置工具
     */
    private boolean isBuiltInTool(String toolName) {
        return BUILTIN_PREFIXES.stream()
            .anyMatch(toolName::startsWith);
    }

    /**
     * 执行内置工具
     */
    private ToolResult executeBuiltInTool(String toolName, Map<String, Object> arguments,
                                           UserContextDTO context) {
        BuiltInToolExecutor executor = builtInToolRegistry.getExecutor(toolName);
        if (executor == null) {
            return ToolResult.failure("Built-in tool not found: " + toolName);
        }
        return executor.execute(arguments, context);
    }

    /**
     * 解析工具名
     */
    private ToolNameInfo parseToolName(String toolName) {
        int underscoreIndex = toolName.indexOf('_');
        if (underscoreIndex <= 0) {
            throw new ToolNotFoundException("Invalid tool name format: " + toolName);
        }

        String prefix = toolName.substring(0, underscoreIndex + 1);
        String actualName = toolName.substring(underscoreIndex + 1);

        return new ToolNameInfo(prefix, actualName);
    }

    /**
     * 创建 MCP 客户端
     */
    private MCPClient createMCPClient(RegisteredSystem system, UserContextDTO context) {
        MCPClient client = new MCPClient(system.getMcpGatewayUrl());

        // 根据认证类型设置认证
        switch (system.getAuthType()) {
            case "bearer":
                if (context.getAccessToken() != null) {
                    client.setAuthToken(context.getAccessToken());
                }
                break;
            case "basic":
                // TODO: 实现 Basic Auth
                break;
            default:
                // 无认证
                break;
        }

        return client;
    }

    /**
     * 获取系统工具列表
     */
    private List<ToolDefinition> getSystemTools(RegisteredSystem system) {
        try {
            MCPClient client = new MCPClient(system.getMcpGatewayUrl());
            MCPResponse response = client.listTools();

            if (response.isSuccess()) {
                return parseToolsFromResponse(response, system.getToolPrefix());
            }
        } catch (Exception e) {
            log.warn("Failed to get tools from system: {}", system.getSystemId(), e);
        }
        return Collections.emptyList();
    }

    /**
     * 从 MCP 响应解析工具列表
     */
    private List<ToolDefinition> parseToolsFromResponse(MCPResponse response, String toolPrefix) {
        List<ToolDefinition> tools = new ArrayList<>();
        JsonNode toolsNode = response.getResult().path("tools");

        if (toolsNode.isArray()) {
            for (JsonNode toolNode : toolsNode) {
                String name = toolNode.path("name").asText();
                // 添加前缀
                String fullName = toolPrefix + name;

                tools.add(ToolDefinition.builder()
                    .name(fullName)
                    .description(toolNode.path("description").asText())
                    .inputSchema(toolNode.path("inputSchema"))
                    .build());
            }
        }

        return tools;
    }

    // 内部类：工具名信息
    @Data
    @AllArgsConstructor
    private static class ToolNameInfo {
        private String prefix;
        private String actualName;
    }
}
```

### 3.2 BuiltInToolRegistry

```java
@Service
@RequiredArgsConstructor
public class BuiltInToolRegistry {

    private final SearchService searchService;
    private final BrowserTool browserTool;

    private final Map<String, BuiltInToolExecutor> executors = new HashMap<>();

    @PostConstruct
    public void init() {
        // 注册搜索工具
        registerTool("web_search", this::executeWebSearch);

        // 注册浏览器工具
        registerTool("browser_navigate", this::executeBrowserNavigate);
        registerTool("browser_snapshot", this::executeBrowserSnapshot);
        registerTool("browser_screenshot", this::executeBrowserScreenshot);
    }

    public void registerTool(String name, BuiltInToolExecutor executor) {
        executors.put(name, executor);
    }

    public BuiltInToolExecutor getExecutor(String name) {
        return executors.get(name);
    }

    public List<ToolDefinition> getAllTools() {
        return List.of(
            ToolDefinition.builder()
                .name("web_search")
                .description("搜索网络信息，返回搜索结果列表")
                .inputSchema(buildWebSearchSchema())
                .build(),
            ToolDefinition.builder()
                .name("browser_navigate")
                .description("打开指定网页")
                .inputSchema(buildBrowserNavigateSchema())
                .build(),
            ToolDefinition.builder()
                .name("browser_snapshot")
                .description("获取当前页面的文本内容快照")
                .inputSchema(buildBrowserSnapshotSchema())
                .build(),
            ToolDefinition.builder()
                .name("browser_screenshot")
                .description("截取当前页面的屏幕截图")
                .inputSchema(buildBrowserScreenshotSchema())
                .build()
        );
    }

    // === 工具执行方法 ===

    private ToolResult executeWebSearch(Map<String, Object> args, UserContextDTO context) {
        String query = (String) args.get("query");
        if (query == null || query.isEmpty()) {
            return ToolResult.failure("query parameter is required");
        }

        int limit = args.containsKey("limit") ? (Integer) args.get("limit") : 5;

        try {
            SearchResult result = searchService.search(query, limit);
            return ToolResult.success(result);
        } catch (Exception e) {
            return ToolResult.failure("Search failed: " + e.getMessage());
        }
    }

    private ToolResult executeBrowserNavigate(Map<String, Object> args, UserContextDTO context) {
        String url = (String) args.get("url");
        if (url == null || url.isEmpty()) {
            return ToolResult.failure("url parameter is required");
        }

        try {
            browserTool.navigate(url);
            return ToolResult.success(Map.of("status", "navigated", "url", url));
        } catch (Exception e) {
            return ToolResult.failure("Navigation failed: " + e.getMessage());
        }
    }

    private ToolResult executeBrowserSnapshot(Map<String, Object> args, UserContextDTO context) {
        try {
            String content = browserTool.getSnapshot();
            return ToolResult.success(Map.of("content", content));
        } catch (Exception e) {
            return ToolResult.failure("Snapshot failed: " + e.getMessage());
        }
    }

    private ToolResult executeBrowserScreenshot(Map<String, Object> args, UserContextDTO context) {
        try {
            String base64Image = browserTool.takeScreenshot();
            return ToolResult.success(Map.of("image", base64Image));
        } catch (Exception e) {
            return ToolResult.failure("Screenshot failed: " + e.getMessage());
        }
    }

    // === Schema 构建 ===

    private JsonNode buildWebSearchSchema() {
        return objectMapper.valueToTree(Map.of(
            "type", "object",
            "properties", Map.of(
                "query", Map.of("type", "string", "description", "搜索关键词"),
                "limit", Map.of("type", "integer", "description", "返回结果数量", "default", 5)
            ),
            "required", List.of("query")
        ));
    }

    private JsonNode buildBrowserNavigateSchema() {
        return objectMapper.valueToTree(Map.of(
            "type", "object",
            "properties", Map.of(
                "url", Map.of("type", "string", "description", "要打开的URL")
            ),
            "required", List.of("url")
        ));
    }

    private JsonNode buildBrowserSnapshotSchema() {
        return objectMapper.valueToTree(Map.of(
            "type", "object",
            "properties", Map.of()
        ));
    }

    private JsonNode buildBrowserScreenshotSchema() {
        return objectMapper.valueToTree(Map.of(
            "type", "object",
            "properties", Map.of()
        ));
    }
}

// 内置工具执行器接口
@FunctionalInterface
public interface BuiltInToolExecutor {
    ToolResult execute(Map<String, Object> arguments, UserContextDTO context);
}
```

---

## 4. 搜索服务设计

### 4.1 SearchProvider 接口

```java
public interface SearchProvider {
    /**
     * 执行搜索
     *
     * @param query 搜索关键词
     * @param limit 返回结果数量
     * @return 搜索结果
     */
    SearchResult search(String query, int limit);

    /**
     * 获取提供商名称
     */
    String getProviderName();
}
```

### 4.2 SerperClient

```java
@Service
@Slf4j
public class SerperClient implements SearchProvider {

    private static final String API_URL = "https://google.serper.dev/search";

    @Value("${search.serper.api-key:}")
    private String apiKey;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public SearchResult search(String query, int limit) {
        if (apiKey == null || apiKey.isEmpty()) {
            throw new SearchException("Serper API key not configured");
        }

        try {
            Map<String, Object> requestBody = Map.of(
                "q", query,
                "num", limit
            );

            String jsonBody = objectMapper.writeValueAsString(requestBody);

            Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("X-API-KEY", apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new SearchException("Serper API error: " + response.code());
                }

                String body = response.body().string();
                return parseSearchResult(body, limit);
            }
        } catch (Exception e) {
            log.error("Serper search failed", e);
            throw new SearchException("Search failed: " + e.getMessage(), e);
        }
    }

    @Override
    public String getProviderName() {
        return "serper";
    }

    private SearchResult parseSearchResult(String body, int limit) throws JsonProcessingException {
        JsonNode root = objectMapper.readTree(body);

        List<SearchResultItem> items = new ArrayList<>();

        // 解析 organic 结果
        JsonNode organic = root.path("organic");
        if (organic.isArray()) {
            for (int i = 0; i < Math.min(organic.size(), limit); i++) {
                JsonNode item = organic.get(i);
                items.add(SearchResultItem.builder()
                    .title(item.path("title").asText())
                    .link(item.path("link").asText())
                    .snippet(item.path("snippet").asText())
                    .build());
            }
        }

        return SearchResult.builder()
            .query(root.path("searchParameters").path("q").asText())
            .items(items)
            .total(items.size())
            .build();
    }
}
```

### 4.3 SearchResult 数据结构

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResult {
    private String query;
    private List<SearchResultItem> items;
    private Integer total;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SearchResultItem {
    private String title;
    private String link;
    private String snippet;
}
```

---

## 5. 浏览器工具设计

### 5.1 BrowserTool

```java
@Service
@Slf4j
@ConditionalOnProperty(name = "browser.enabled", havingValue = "true", matchIfMissing = false)
public class BrowserTool {

    @Value("${browser.playwright-url:}")
    private String playwrightUrl;

    private final OkHttpClient httpClient = new OkHttpClient();
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 导航到指定 URL
     */
    public void navigate(String url) {
        if (playwrightUrl == null || playwrightUrl.isEmpty()) {
            throw new BrowserException("Playwright service not configured");
        }

        try {
            Map<String, Object> request = Map.of(
                "action", "navigate",
                "url", url
            );

            sendRequest(request);
        } catch (Exception e) {
            throw new BrowserException("Navigate failed: " + e.getMessage(), e);
        }
    }

    /**
     * 获取页面快照
     */
    public String getSnapshot() {
        if (playwrightUrl == null || playwrightUrl.isEmpty()) {
            throw new BrowserException("Playwright service not configured");
        }

        try {
            Map<String, Object> request = Map.of(
                "action", "snapshot"
            );

            JsonNode response = sendRequest(request);
            return response.path("content").asText();
        } catch (Exception e) {
            throw new BrowserException("Snapshot failed: " + e.getMessage(), e);
        }
    }

    /**
     * 截取屏幕截图
     */
    public String takeScreenshot() {
        if (playwrightUrl == null || playwrightUrl.isEmpty()) {
            throw new BrowserException("Playwright service not configured");
        }

        try {
            Map<String, Object> request = Map.of(
                "action", "screenshot"
            );

            JsonNode response = sendRequest(request);
            return response.path("image").asText(); // Base64 编码
        } catch (Exception e) {
            throw new BrowserException("Screenshot failed: " + e.getMessage(), e);
        }
    }

    private JsonNode sendRequest(Map<String, Object> request) throws IOException {
        String jsonBody = objectMapper.writeValueAsString(request);

        Request httpRequest = new Request.Builder()
            .url(playwrightUrl + "/api/browser")
            .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
            .build();

        try (Response response = httpClient.newCall(httpRequest).execute()) {
            if (!response.isSuccessful()) {
                throw new BrowserException("Browser API error: " + response.code());
            }

            return objectMapper.readTree(response.body().string());
        }
    }
}
```

---

## 6. 异常设计

### 6.1 异常类

```java
// 工具未找到异常
public class ToolNotFoundException extends RuntimeException {
    public ToolNotFoundException(String message) {
        super(message);
    }
}

// 工具执行异常
public class ToolExecutionException extends RuntimeException {
    public ToolExecutionException(String message) {
        super(message);
    }

    public ToolExecutionException(String message, Throwable cause) {
        super(message, cause);
    }
}

// 搜索异常
public class SearchException extends RuntimeException {
    public SearchException(String message) {
        super(message);
    }

    public SearchException(String message, Throwable cause) {
        super(message, cause);
    }
}

// 浏览器异常
public class BrowserException extends RuntimeException {
    public BrowserException(String message) {
        super(message);
    }

    public BrowserException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

---

## 7. 配置设计

### 7.1 application.yml

```yaml
# 搜索配置
search:
  provider: serper  # serper 或 tavily
  serper:
    api-key: ${SERPER_API_KEY:}
  tavily:
    api-key: ${TAVILY_API_KEY:}

# 浏览器工具配置
browser:
  enabled: false
  playwright-url: ${PLAYWRIGHT_URL:}

# MCP 配置
mcp:
  timeout: 30000  # 30秒超时
  retry: 2        # 失败重试次数
```

---

## 8. 测试用例

### 8.1 调用系统工具

```java
@Test
void testExecuteSystemTool() {
    // 准备
    String toolName = "osrm_search_software";
    Map<String, Object> args = Map.of("keyword", "nginx");
    UserContextDTO context = UserContextDTO.builder()
        .sessionId("test-session")
        .systemId("osrm")
        .accessToken("test-token")
        .build();

    // 执行
    ToolResult result = toolRouter.execute(toolName, args, context);

    // 验证
    assertTrue(result.isSuccess());
    assertNotNull(result.getData());
}
```

### 8.2 调用内置工具

```java
@Test
void testExecuteBuiltInTool() {
    // 准备
    String toolName = "web_search";
    Map<String, Object> args = Map.of("query", "Spring Boot");

    // 执行
    ToolResult result = toolRouter.execute(toolName, args, null);

    // 验证
    assertTrue(result.isSuccess());
    SearchResult searchResult = (SearchResult) result.getData();
    assertNotNull(searchResult.getItems());
}
```

### 8.3 工具未找到

```java
@Test
void testToolNotFound() {
    // 准备
    String toolName = "unknown_tool";
    Map<String, Object> args = Map.of();

    // 执行
    ToolResult result = toolRouter.execute(toolName, args, null);

    // 验证
    assertFalse(result.isSuccess());
    assertTrue(result.getError().contains("not found"));
}
```

---

## 9. 错误码定义

| 错误码 | 说明 |
|--------|------|
| TOOL_NOT_FOUND | 工具未找到 |
| TOOL_EXECUTION_FAILED | 工具执行失败 |
| SYSTEM_NOT_FOUND | 目标系统未找到 |
| SEARCH_FAILED | 搜索失败 |
| BROWSER_ERROR | 浏览器操作错误 |
| MCP_TIMEOUT | MCP 调用超时 |
