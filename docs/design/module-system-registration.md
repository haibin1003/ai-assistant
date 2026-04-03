# 系统注册管理模块详细设计

## 文档信息

- **模块名称**: 系统注册管理 (System Registration)
- **版本**: 1.0.0
- **创建日期**: 2026-03-25
- **文档类型**: 模块详细设计文档

---

## 1. 模块概述

### 1.1 功能描述

系统注册管理模块负责管理可接入 AI 助手的目标系统，包括：

- 注册新系统
- 更新系统配置
- 注销系统
- 工具发现与缓存
- 系统健康检查

### 1.2 模块位置

```
com.osrm.ai
├── domain.system           # 领域层
│   ├── entity/
│   │   └── RegisteredSystem.java
│   └── repository/
│       └── RegisteredSystemRepository.java
├── application.system      # 应用服务层
│   ├── SystemService.java
│   └── dto/
│       ├── RegisterSystemRequest.java
│       ├── UpdateSystemRequest.java
│       └── RegisteredSystemDTO.java
├── infrastructure.mcp      # 基础设施层
│   ├── MCPClient.java
│   └── ToolDiscovery.java
└── interfaces.rest         # 接口层
    └── SystemController.java
```

---

## 2. 领域模型

### 2.1 RegisteredSystem 实体

```java
@Entity
@Table(name = "t_registered_system")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisteredSystem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 系统唯一标识
     * 示例: osrm, systemb
     */
    @Column(name = "system_id", unique = true, nullable = false, length = 64)
    private String systemId;

    /**
     * 系统名称
     */
    @Column(name = "system_name", nullable = false, length = 128)
    private String systemName;

    /**
     * 系统图标 URL
     */
    @Column(name = "icon_url", length = 512)
    private String iconUrl;

    /**
     * MCP 网关地址
     */
    @Column(name = "mcp_gateway_url", nullable = false, length = 512)
    private String mcpGatewayUrl;

    /**
     * 认证类型: none, basic, bearer
     */
    @Column(name = "auth_type", nullable = false, length = 32)
    @Builder.Default
    private String authType = "none";

    /**
     * 工具名前缀
     * 示例: osrm_, systemb_
     */
    @Column(name = "tool_prefix", nullable = false, length = 32)
    private String toolPrefix;

    /**
     * 系统描述
     */
    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * 是否启用
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

### 2.2 ToolCache 实体

```java
@Entity
@Table(name = "t_tool_cache")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "system_id", nullable = false, length = 64)
    private String systemId;

    @Column(name = "tool_name", nullable = false, length = 128)
    private String toolName;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    /**
     * JSON 格式的输入 Schema
     */
    @Column(name = "input_schema", nullable = false, columnDefinition = "TEXT")
    private String inputSchema;

    @Column(name = "fetched_at", nullable = false)
    private LocalDateTime fetchedAt;
}
```

---

## 3. 服务设计

### 3.1 SystemService

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class SystemService {

    private final RegisteredSystemRepository systemRepository;
    private final ToolCacheRepository toolCacheRepository;
    private final ToolDiscovery toolDiscovery;

    /**
     * 注册新系统
     */
    @Transactional
    public RegisteredSystemDTO registerSystem(RegisterSystemRequest request) {
        // 1. 检查 systemId 是否已存在
        if (systemRepository.findBySystemId(request.getSystemId()).isPresent()) {
            throw new BizException("SYSTEM_ALREADY_EXISTS",
                "System already registered: " + request.getSystemId());
        }

        // 2. 创建系统实体
        RegisteredSystem system = RegisteredSystem.builder()
            .systemId(request.getSystemId())
            .systemName(request.getSystemName())
            .iconUrl(request.getIconUrl())
            .mcpGatewayUrl(request.getMcpGatewayUrl())
            .authType(request.getAuthType() != null ? request.getAuthType() : "none")
            .toolPrefix(request.getToolPrefix())
            .description(request.getDescription())
            .isActive(true)
            .build();

        systemRepository.save(system);

        // 3. 发现并缓存工具
        discoverAndCacheTools(system);

        log.info("System registered: {}", system.getSystemId());
        return toDTO(system);
    }

    /**
     * 更新系统配置
     */
    @Transactional
    public RegisteredSystemDTO updateSystem(String systemId, UpdateSystemRequest request) {
        RegisteredSystem system = systemRepository.findBySystemId(systemId)
            .orElseThrow(() -> new BizException("SYSTEM_NOT_FOUND",
                "System not found: " + systemId));

        // 更新字段
        if (request.getSystemName() != null) {
            system.setSystemName(request.getSystemName());
        }
        if (request.getIconUrl() != null) {
            system.setIconUrl(request.getIconUrl());
        }
        if (request.getMcpGatewayUrl() != null) {
            system.setMcpGatewayUrl(request.getMcpGatewayUrl());
            // URL 变更，刷新工具缓存
            discoverAndCacheTools(system);
        }
        if (request.getAuthType() != null) {
            system.setAuthType(request.getAuthType());
        }
        if (request.getDescription() != null) {
            system.setDescription(request.getDescription());
        }

        systemRepository.save(system);
        log.info("System updated: {}", systemId);
        return toDTO(system);
    }

    /**
     * 注销系统
     */
    @Transactional
    public void unregisterSystem(String systemId) {
        RegisteredSystem system = systemRepository.findBySystemId(systemId)
            .orElseThrow(() -> new BizException("SYSTEM_NOT_FOUND",
                "System not found: " + systemId));

        system.setIsActive(false);
        systemRepository.save(system);

        // 清理工具缓存
        toolCacheRepository.deleteBySystemId(systemId);

        log.info("System unregistered: {}", systemId);
    }

    /**
     * 获取系统详情
     */
    public RegisteredSystemDTO getSystem(String systemId) {
        RegisteredSystem system = systemRepository.findBySystemId(systemId)
            .orElseThrow(() -> new BizException("SYSTEM_NOT_FOUND",
                "System not found: " + systemId));
        return toDTO(system);
    }

    /**
     * 获取所有活跃系统
     */
    public List<RegisteredSystemDTO> getAllSystems() {
        return systemRepository.findByIsActiveTrue().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * 刷新工具缓存
     */
    @Transactional
    public List<ToolDefinition> refreshToolCache(String systemId) {
        RegisteredSystem system = systemRepository.findBySystemId(systemId)
            .orElseThrow(() -> new BizException("SYSTEM_NOT_FOUND",
                "System not found: " + systemId));

        return discoverAndCacheTools(system);
    }

    /**
     * 获取系统工具列表
     */
    public List<ToolDefinition> getSystemTools(String systemId) {
        List<ToolCache> cachedTools = toolCacheRepository.findBySystemId(systemId);

        if (cachedTools.isEmpty()) {
            // 缓存为空，尝试发现
            RegisteredSystem system = systemRepository.findBySystemId(systemId)
                .orElseThrow(() -> new BizException("SYSTEM_NOT_FOUND",
                    "System not found: " + systemId));
            return discoverAndCacheTools(system);
        }

        return cachedTools.stream()
            .map(this::toToolDefinition)
            .collect(Collectors.toList());
    }

    /**
     * 健康检查
     */
    public SystemHealth checkHealth(String systemId) {
        RegisteredSystem system = systemRepository.findBySystemId(systemId)
            .orElseThrow(() -> new BizException("SYSTEM_NOT_FOUND",
                "System not found: " + systemId));

        try {
            boolean healthy = toolDiscovery.checkHealth(system.getMcpGatewayUrl());
            return SystemHealth.builder()
                .systemId(systemId)
                .status(healthy ? "UP" : "DOWN")
                .checkedAt(LocalDateTime.now())
                .build();
        } catch (Exception e) {
            return SystemHealth.builder()
                .systemId(systemId)
                .status("DOWN")
                .error(e.getMessage())
                .checkedAt(LocalDateTime.now())
                .build();
        }
    }

    // === 私有方法 ===

    private List<ToolDefinition> discoverAndCacheTools(RegisteredSystem system) {
        try {
            List<ToolDefinition> tools = toolDiscovery.discoverTools(system.getMcpGatewayUrl());

            // 清理旧缓存
            toolCacheRepository.deleteBySystemId(system.getSystemId());

            // 保存新缓存
            List<ToolCache> cacheList = tools.stream()
                .map(tool -> ToolCache.builder()
                    .systemId(system.getSystemId())
                    .toolName(tool.getName())
                    .description(tool.getDescription())
                    .inputSchema(toJson(tool.getInputSchema()))
                    .fetchedAt(LocalDateTime.now())
                    .build())
                .collect(Collectors.toList());
            toolCacheRepository.saveAll(cacheList);

            log.info("Discovered {} tools for system: {}", tools.size(), system.getSystemId());
            return tools;
        } catch (Exception e) {
            log.error("Failed to discover tools for system: {}", system.getSystemId(), e);
            return Collections.emptyList();
        }
    }

    private RegisteredSystemDTO toDTO(RegisteredSystem system) {
        return RegisteredSystemDTO.builder()
            .systemId(system.getSystemId())
            .systemName(system.getSystemName())
            .iconUrl(system.getIconUrl())
            .mcpGatewayUrl(system.getMcpGatewayUrl())
            .authType(system.getAuthType())
            .toolPrefix(system.getToolPrefix())
            .description(system.getDescription())
            .isActive(system.getIsActive())
            .createdAt(system.getCreatedAt())
            .updatedAt(system.getUpdatedAt())
            .build();
    }

    private ToolDefinition toToolDefinition(ToolCache cache) {
        return ToolDefinition.builder()
            .name(cache.getToolName())
            .description(cache.getDescription())
            .inputSchema(parseJson(cache.getInputSchema()))
            .build();
    }
}
```

### 3.2 ToolDiscovery

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ToolDiscovery {

    private final ObjectMapper objectMapper;

    /**
     * 发现目标系统的工具列表
     */
    public List<ToolDefinition> discoverTools(String mcpGatewayUrl) {
        MCPClient client = new MCPClient(mcpGatewayUrl);

        MCPResponse response = client.listTools();

        if (!response.isSuccess()) {
            throw new BizException("TOOL_DISCOVERY_FAILED",
                "Failed to discover tools: " + response.getError());
        }

        List<ToolDefinition> tools = new ArrayList<>();
        JsonNode toolsNode = response.getResult().path("tools");

        if (toolsNode.isArray()) {
            for (JsonNode toolNode : toolsNode) {
                ToolDefinition tool = ToolDefinition.builder()
                    .name(toolNode.path("name").asText())
                    .description(toolNode.path("description").asText())
                    .inputSchema(toolNode.path("inputSchema"))
                    .build();
                tools.add(tool);
            }
        }

        return tools;
    }

    /**
     * 健康检查
     */
    public boolean checkHealth(String mcpGatewayUrl) {
        try {
            MCPClient client = new MCPClient(mcpGatewayUrl);
            MCPResponse response = client.ping();
            return response.isSuccess();
        } catch (Exception e) {
            log.warn("Health check failed for {}: {}", mcpGatewayUrl, e.getMessage());
            return false;
        }
    }
}
```

---

## 4. MCP Client 设计

### 4.1 MCPClient

```java
@Slf4j
public class MCPClient {

    private static final String JSON_RPC_VERSION = "2.0";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String gatewayUrl;
    private final OkHttpClient httpClient;
    private String authToken;

    public MCPClient(String gatewayUrl) {
        this.gatewayUrl = gatewayUrl;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    /**
     * 设置认证令牌
     */
    public void setAuthToken(String authToken) {
        this.authToken = authToken;
    }

    /**
     * 列出可用工具
     */
    public MCPResponse listTools() {
        MCPRequest request = MCPRequest.builder()
            .jsonrpc(JSON_RPC_VERSION)
            .id(generateId())
            .method("tools/list")
            .params(Map.of())
            .build();

        return sendRequest(request);
    }

    /**
     * 调用工具
     */
    public MCPResponse callTool(String toolName, Map<String, Object> arguments) {
        MCPRequest request = MCPRequest.builder()
            .jsonrpc(JSON_RPC_VERSION)
            .id(generateId())
            .method("tools/call")
            .params(Map.of(
                "name", toolName,
                "arguments", arguments
            ))
            .build();

        return sendRequest(request);
    }

    /**
     * Ping 检查
     */
    public MCPResponse ping() {
        MCPRequest request = MCPRequest.builder()
            .jsonrpc(JSON_RPC_VERSION)
            .id(generateId())
            .method("ping")
            .params(Map.of())
            .build();

        return sendRequest(request);
    }

    // === 私有方法 ===

    private MCPResponse sendRequest(MCPRequest request) {
        try {
            String requestBody = OBJECT_MAPPER.writeValueAsString(request);

            Request.Builder httpRequestBuilder = new Request.Builder()
                .url(gatewayUrl)
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")));

            // 添加认证头
            if (authToken != null && !authToken.isEmpty()) {
                httpRequestBuilder.addHeader("Authorization", "Bearer " + authToken);
            }

            try (Response response = httpClient.newCall(httpRequestBuilder.build()).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "{}";

                if (!response.isSuccessful()) {
                    return MCPResponse.builder()
                        .error(Map.of(
                            "code", response.code(),
                            "message", "HTTP error: " + response.message()
                        ))
                        .build();
                }

                return OBJECT_MAPPER.readValue(responseBody, MCPResponse.class);
            }
        } catch (Exception e) {
            log.error("MCP request failed", e);
            return MCPResponse.builder()
                .error(Map.of(
                    "code", -1,
                    "message", e.getMessage()
                ))
                .build();
        }
    }

    private String generateId() {
        return UUID.randomUUID().toString();
    }
}
```

### 4.2 MCP 数据结构

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPRequest {
    private String jsonrpc;
    private String id;
    private String method;
    private Map<String, Object> params;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPResponse {
    private String jsonrpc;
    private String id;
    private Map<String, Object> result;
    private Map<String, Object> error;

    public boolean isSuccess() {
        return error == null;
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolDefinition {
    private String name;
    private String description;
    private JsonNode inputSchema;
}
```

---

## 5. REST 接口设计

### 5.1 SystemController

```java
@RestController
@RequestMapping("/api/v1/systems")
@RequiredArgsConstructor
@Slf4j
public class SystemController {

    private final SystemService systemService;

    /**
     * 获取所有已注册系统
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RegisteredSystemDTO>>> getAllSystems() {
        List<RegisteredSystemDTO> systems = systemService.getAllSystems();
        return ResponseEntity.ok(ApiResponse.success(systems));
    }

    /**
     * 注册新系统
     */
    @PostMapping
    public ResponseEntity<ApiResponse<RegisteredSystemDTO>> registerSystem(
            @Valid @RequestBody RegisterSystemRequest request) {
        RegisteredSystemDTO system = systemService.registerSystem(request);
        return ResponseEntity.ok(ApiResponse.success(system));
    }

    /**
     * 获取系统详情
     */
    @GetMapping("/{systemId}")
    public ResponseEntity<ApiResponse<RegisteredSystemDTO>> getSystem(
            @PathVariable String systemId) {
        RegisteredSystemDTO system = systemService.getSystem(systemId);
        return ResponseEntity.ok(ApiResponse.success(system));
    }

    /**
     * 更新系统配置
     */
    @PutMapping("/{systemId}")
    public ResponseEntity<ApiResponse<RegisteredSystemDTO>> updateSystem(
            @PathVariable String systemId,
            @Valid @RequestBody UpdateSystemRequest request) {
        RegisteredSystemDTO system = systemService.updateSystem(systemId, request);
        return ResponseEntity.ok(ApiResponse.success(system));
    }

    /**
     * 注销系统
     */
    @DeleteMapping("/{systemId}")
    public ResponseEntity<ApiResponse<Void>> unregisterSystem(
            @PathVariable String systemId) {
        systemService.unregisterSystem(systemId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 刷新工具缓存
     */
    @PostMapping("/{systemId}/refresh-tools")
    public ResponseEntity<ApiResponse<List<ToolDefinition>>> refreshToolCache(
            @PathVariable String systemId) {
        List<ToolDefinition> tools = systemService.refreshToolCache(systemId);
        return ResponseEntity.ok(ApiResponse.success(tools));
    }

    /**
     * 获取系统工具列表
     */
    @GetMapping("/{systemId}/tools")
    public ResponseEntity<ApiResponse<List<ToolDefinition>>> getSystemTools(
            @PathVariable String systemId) {
        List<ToolDefinition> tools = systemService.getSystemTools(systemId);
        return ResponseEntity.ok(ApiResponse.success(tools));
    }

    /**
     * 系统健康检查
     */
    @GetMapping("/{systemId}/health")
    public ResponseEntity<ApiResponse<SystemHealth>> checkHealth(
            @PathVariable String systemId) {
        SystemHealth health = systemService.checkHealth(systemId);
        return ResponseEntity.ok(ApiResponse.success(health));
    }
}
```

### 5.2 请求/响应 DTO

```java
// 注册系统请求
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisterSystemRequest {
    @NotBlank(message = "系统ID不能为空")
    @Size(max = 64, message = "系统ID最长64字符")
    private String systemId;

    @NotBlank(message = "系统名称不能为空")
    @Size(max = 128, message = "系统名称最长128字符")
    private String systemName;

    @Size(max = 512, message = "图标URL最长512字符")
    private String iconUrl;

    @NotBlank(message = "MCP网关地址不能为空")
    @Size(max = 512, message = "MCP网关地址最长512字符")
    private String mcpGatewayUrl;

    private String authType; // none, basic, bearer

    @NotBlank(message = "工具前缀不能为空")
    @Size(max = 32, message = "工具前缀最长32字符")
    private String toolPrefix;

    private String description;
}

// 更新系统请求
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSystemRequest {
    @Size(max = 128, message = "系统名称最长128字符")
    private String systemName;

    @Size(max = 512, message = "图标URL最长512字符")
    private String iconUrl;

    @Size(max = 512, message = "MCP网关地址最长512字符")
    private String mcpGatewayUrl;

    private String authType;

    private String description;
}

// 系统详情 DTO
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RegisteredSystemDTO {
    private String systemId;
    private String systemName;
    private String iconUrl;
    private String mcpGatewayUrl;
    private String authType;
    private String toolPrefix;
    private String description;
    private Boolean isActive;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

// 系统健康状态
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SystemHealth {
    private String systemId;
    private String status; // UP, DOWN
    private String error;
    private LocalDateTime checkedAt;
}
```

---

## 6. 数据库设计

### 6.1 t_registered_system 表

```sql
CREATE TABLE t_registered_system (
    id              BIGINT UNSIGNED     PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    system_id       VARCHAR(64)         NOT NULL COMMENT '系统唯一标识',
    system_name     VARCHAR(128)        NOT NULL COMMENT '系统名称',
    icon_url        VARCHAR(512)        COMMENT '系统图标URL',
    mcp_gateway_url VARCHAR(512)        NOT NULL COMMENT 'MCP网关地址',
    auth_type       VARCHAR(32)         NOT NULL DEFAULT 'none' COMMENT '认证类型: none/basic/bearer',
    tool_prefix     VARCHAR(32)         NOT NULL COMMENT '工具名前缀',
    description     TEXT                COMMENT '系统描述',
    is_active       TINYINT             NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_at      TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_system_id (system_id),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='目标系统注册表';
```

### 6.2 t_tool_cache 表

```sql
CREATE TABLE t_tool_cache (
    id              BIGINT UNSIGNED     PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    system_id       VARCHAR(64)         NOT NULL COMMENT '系统ID',
    tool_name       VARCHAR(128)        NOT NULL COMMENT '工具名称',
    description     TEXT                COMMENT '工具描述',
    input_schema    TEXT                NOT NULL COMMENT '输入Schema(JSON)',
    fetched_at      TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '获取时间',

    UNIQUE KEY uk_system_tool (system_id, tool_name),
    INDEX idx_system_id (system_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='工具缓存表';
```

---

## 7. 接口测试用例

### 7.1 注册系统

```http
POST /api/v1/systems
Content-Type: application/json

{
  "systemId": "osrm",
  "systemName": "开源软件仓库管理",
  "iconUrl": "https://osrm.example.com/icon.png",
  "mcpGatewayUrl": "http://osrm-mcp:3000/api/mcp",
  "authType": "bearer",
  "toolPrefix": "osrm_",
  "description": "OSRM 系统描述"
}

# 期望响应
{
  "code": 200,
  "message": "success",
  "data": {
    "systemId": "osrm",
    "systemName": "开源软件仓库管理",
    "iconUrl": "https://osrm.example.com/icon.png",
    "mcpGatewayUrl": "http://osrm-mcp:3000/api/mcp",
    "authType": "bearer",
    "toolPrefix": "osrm_",
    "description": "OSRM 系统描述",
    "isActive": true,
    "createdAt": "2026-03-25T10:00:00",
    "updatedAt": "2026-03-25T10:00:00"
  }
}
```

### 7.2 获取系统列表

```http
GET /api/v1/systems

# 期望响应
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "systemId": "osrm",
      "systemName": "开源软件仓库管理",
      ...
    }
  ]
}
```

### 7.3 刷新工具缓存

```http
POST /api/v1/systems/osrm/refresh-tools

# 期望响应
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "name": "search_software",
      "description": "搜索软件",
      "inputSchema": {...}
    }
  ]
}
```

---

## 8. 错误码定义

| 错误码 | HTTP 状态 | 说明 |
|--------|----------|------|
| SYSTEM_ALREADY_EXISTS | 400 | 系统已注册 |
| SYSTEM_NOT_FOUND | 404 | 系统不存在 |
| TOOL_DISCOVERY_FAILED | 500 | 工具发现失败 |
| MCP_CONNECTION_ERROR | 503 | MCP 连接错误 |
