# 用户上下文管理模块详细设计

## 文档信息

- **模块名称**: 用户上下文管理 (User Context Management)
- **版本**: 1.0.0
- **创建日期**: 2026-03-25
- **文档类型**: 模块详细设计文档

---

## 1. 模块概述

### 1.1 功能描述

用户上下文管理模块负责：

- 接收目标系统推送的用户上下文
- 管理用户会话生命周期
- 用户权限信息缓存
- 会话过期清理

### 1.2 模块位置

```
com.osrm.ai
├── domain.session           # 领域层
│   ├── entity/
│   │   └── Session.java
│   └── repository/
│       └── SessionRepository.java
├── application.context      # 应用服务层
│   ├── ContextService.java
│   └── dto/
│       ├── PushContextRequest.java
│       └── UserContextDTO.java
└── interfaces.rest          # 接口层
    └── ContextController.java
```

---

## 2. 领域模型

### 2.1 Session 实体

```java
@Entity
@Table(name = "t_session")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Session {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 会话唯一标识
     * 由目标系统生成并维护
     */
    @Column(name = "session_id", unique = true, nullable = false, length = 64)
    private String sessionId;

    /**
     * 目标系统ID
     */
    @Column(name = "system_id", nullable = false, length = 64)
    private String systemId;

    /**
     * 用户ID
     */
    @Column(name = "user_id", length = 64)
    private String userId;

    /**
     * 用户名
     */
    @Column(name = "username", length = 128)
    private String username;

    /**
     * 用户上下文 JSON
     * 包含: userId, username, roles, permissions, realName, email
     */
    @Column(name = "user_context", columnDefinition = "TEXT")
    private String userContext;

    /**
     * 访问令牌 (加密存储)
     */
    @Column(name = "access_token", columnDefinition = "TEXT")
    private String accessToken;

    /**
     * 刷新令牌 (加密存储)
     */
    @Column(name = "refresh_token", columnDefinition = "TEXT")
    private String refreshToken;

    /**
     * 令牌过期时间
     */
    @Column(name = "token_expires_at")
    private LocalDateTime tokenExpiresAt;

    /**
     * 会话过期时间
     */
    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    /**
     * 是否已删除
     */
    @Column(name = "is_deleted")
    @Builder.Default
    private Boolean isDeleted = false;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 会话是否过期
     */
    public boolean isExpired() {
        return LocalDateTime.now().isAfter(expiresAt);
    }

    /**
     * 令牌是否过期
     */
    public boolean isTokenExpired() {
        return tokenExpiresAt != null && LocalDateTime.now().isAfter(tokenExpiresAt);
    }
}
```

### 2.2 UserContext 值对象

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserContextDTO {

    private String sessionId;
    private String systemId;
    private String userId;
    private String username;
    private List<String> roles;
    private List<String> permissions;
    private String accessToken;
    private LocalDateTime expiresAt;
    private Map<String, Object> extraData;

    /**
     * 是否有效
     */
    public boolean isValid() {
        return expiresAt != null && LocalDateTime.now().isBefore(expiresAt);
    }

    /**
     * 构建系统提示词中的用户信息
     */
    public String toSystemPromptSection() {
        StringBuilder sb = new StringBuilder();
        sb.append("## 用户信息\n");
        sb.append("- 用户名: ").append(username).append("\n");
        sb.append("- 用户ID: ").append(userId).append("\n");
        if (roles != null && !roles.isEmpty()) {
            sb.append("- 角色: ").append(String.join(", ", roles)).append("\n");
        }
        if (permissions != null && !permissions.isEmpty()) {
            sb.append("- 权限: ").append(String.join(", ", permissions)).append("\n");
        }
        return sb.toString();
    }
}
```

---

## 3. 服务设计

### 3.1 ContextService

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ContextService {

    private final SessionRepository sessionRepository;
    private final ObjectMapper objectMapper;

    // 内存缓存，用于快速访问
    private final Map<String, UserContextDTO> contextCache = new ConcurrentHashMap<>();

    // 会话默认过期时间 (小时)
    private static final int DEFAULT_SESSION_HOURS = 24;

    /**
     * 推送用户上下文
     *
     * @param request 上下文推送请求
     */
    @Transactional
    public void pushContext(PushContextRequest request) {
        String sessionId = request.getSessionId();

        // 计算过期时间
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(DEFAULT_SESSION_HOURS);
        LocalDateTime tokenExpiresAt = null;
        if (request.getCredentials() != null && request.getCredentials().getExpiresIn() != null) {
            tokenExpiresAt = LocalDateTime.now().plusSeconds(request.getCredentials().getExpiresIn());
        }

        // 构建用户上下文 JSON
        String userContextJson = buildUserContextJson(request);

        // 查找或创建 Session
        Optional<Session> existingSession = sessionRepository.findBySessionId(sessionId);
        Session session;

        if (existingSession.isPresent()) {
            session = existingSession.get();
            updateSession(session, request, userContextJson, tokenExpiresAt, expiresAt);
        } else {
            session = createSession(request, userContextJson, tokenExpiresAt, expiresAt);
        }

        sessionRepository.save(session);

        // 更新缓存
        UserContextDTO dto = buildDTO(session, request);
        contextCache.put(sessionId, dto);

        log.info("Context pushed for session: {}, user: {}, system: {}",
                sessionId, request.getUser().getUsername(), request.getSystemId());
    }

    /**
     * 获取用户上下文
     *
     * @param sessionId 会话ID
     * @return 用户上下文，如果不存在或已过期返回 null
     */
    public UserContextDTO getContext(String sessionId) {
        // 先查缓存
        UserContextDTO cached = contextCache.get(sessionId);
        if (cached != null && cached.isValid()) {
            return cached;
        }

        // 缓存未命中，查数据库
        Optional<Session> sessionOpt = sessionRepository.findBySessionId(sessionId);
        if (sessionOpt.isEmpty()) {
            return null;
        }

        Session session = sessionOpt.get();
        if (session.isExpired() || session.getIsDeleted()) {
            cleanupSession(session);
            return null;
        }

        // 重建 DTO 并缓存
        UserContextDTO dto = buildDTOFromSession(session);
        contextCache.put(sessionId, dto);
        return dto;
    }

    /**
     * 清除用户上下文
     *
     * @param sessionId 会话ID
     */
    @Transactional
    public void clearContext(String sessionId) {
        sessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            session.setIsDeleted(true);
            sessionRepository.save(session);
        });
        contextCache.remove(sessionId);
        log.info("Context cleared for session: {}", sessionId);
    }

    /**
     * 获取会话过期时间
     *
     * @param sessionId 会话ID
     * @return 过期时间，如果会话不存在返回 null
     */
    public LocalDateTime getSessionExpiresAt(String sessionId) {
        UserContextDTO context = getContext(sessionId);
        return context != null ? context.getExpiresAt() : null;
    }

    /**
     * 检查会话是否存在且有效
     */
    public boolean isSessionValid(String sessionId) {
        return getContext(sessionId) != null;
    }

    /**
     * 获取用户的访问令牌
     */
    public String getAccessToken(String sessionId) {
        UserContextDTO context = getContext(sessionId);
        return context != null ? context.getAccessToken() : null;
    }

    /**
     * 清理过期会话 (定时任务)
     */
    @Scheduled(fixedRate = 3600000) // 每小时执行一次
    @Transactional
    public void cleanupExpiredSessions() {
        log.info("Starting expired sessions cleanup...");

        List<Session> expiredSessions = sessionRepository.findByExpiresAtBeforeAndIsDeletedFalse(
            LocalDateTime.now()
        );

        for (Session session : expiredSessions) {
            cleanupSession(session);
        }

        log.info("Cleaned up {} expired sessions", expiredSessions.size());
    }

    // === 私有方法 ===

    private String buildUserContextJson(PushContextRequest request) {
        try {
            Map<String, Object> contextMap = new LinkedHashMap<>();
            contextMap.put("userId", request.getUser().getId());
            contextMap.put("username", request.getUser().getUsername());
            contextMap.put("roles", request.getUser().getRoles());
            contextMap.put("permissions", request.getUser().getPermissions());
            contextMap.put("realName", request.getUser().getRealName());
            contextMap.put("email", request.getUser().getEmail());
            return objectMapper.writeValueAsString(contextMap);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize user context", e);
            return "{}";
        }
    }

    private Session createSession(PushContextRequest request, String userContextJson,
                                   LocalDateTime tokenExpiresAt, LocalDateTime expiresAt) {
        return Session.builder()
            .sessionId(request.getSessionId())
            .systemId(request.getSystemId())
            .userId(request.getUser().getId())
            .username(request.getUser().getUsername())
            .userContext(userContextJson)
            .accessToken(request.getCredentials() != null ?
                encryptToken(request.getCredentials().getAccessToken()) : null)
            .refreshToken(request.getCredentials() != null ?
                encryptToken(request.getCredentials().getRefreshToken()) : null)
            .tokenExpiresAt(tokenExpiresAt)
            .expiresAt(expiresAt)
            .isDeleted(false)
            .build();
    }

    private void updateSession(Session session, PushContextRequest request,
                                String userContextJson, LocalDateTime tokenExpiresAt,
                                LocalDateTime expiresAt) {
        session.setSystemId(request.getSystemId());
        session.setUserId(request.getUser().getId());
        session.setUsername(request.getUser().getUsername());
        session.setUserContext(userContextJson);
        if (request.getCredentials() != null) {
            session.setAccessToken(encryptToken(request.getCredentials().getAccessToken()));
            session.setRefreshToken(encryptToken(request.getCredentials().getRefreshToken()));
            session.setTokenExpiresAt(tokenExpiresAt);
        }
        session.setExpiresAt(expiresAt);
        session.setIsDeleted(false);
    }

    private UserContextDTO buildDTO(Session session, PushContextRequest request) {
        return UserContextDTO.builder()
            .sessionId(session.getSessionId())
            .systemId(session.getSystemId())
            .userId(request.getUser().getId())
            .username(request.getUser().getUsername())
            .roles(request.getUser().getRoles())
            .permissions(request.getUser().getPermissions())
            .accessToken(request.getCredentials() != null ?
                request.getCredentials().getAccessToken() : null)
            .expiresAt(session.getExpiresAt())
            .build();
    }

    private UserContextDTO buildDTOFromSession(Session session) {
        // 解析 userContext JSON
        List<String> roles = Collections.emptyList();
        List<String> permissions = Collections.emptyList();

        if (session.getUserContext() != null) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> contextMap = objectMapper.readValue(
                    session.getUserContext(), Map.class
                );
                roles = (List<String>) contextMap.getOrDefault("roles", Collections.emptyList());
                permissions = (List<String>) contextMap.getOrDefault("permissions", Collections.emptyList());
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse user context JSON", e);
            }
        }

        return UserContextDTO.builder()
            .sessionId(session.getSessionId())
            .systemId(session.getSystemId())
            .userId(session.getUserId())
            .username(session.getUsername())
            .roles(roles)
            .permissions(permissions)
            .accessToken(decryptToken(session.getAccessToken()))
            .expiresAt(session.getExpiresAt())
            .build();
    }

    private void cleanupSession(Session session) {
        session.setIsDeleted(true);
        sessionRepository.save(session);
        contextCache.remove(session.getSessionId());
    }

    // TODO: 实现加密/解密
    private String encryptToken(String token) {
        if (token == null) return null;
        // 使用 AES 加密
        return token; // 暂时不加密，后续实现
    }

    private String decryptToken(String encryptedToken) {
        if (encryptedToken == null) return null;
        // 使用 AES 解密
        return encryptedToken; // 暂时不解密，后续实现
    }
}
```

---

## 4. REST 接口设计

### 4.1 ContextController

```java
@RestController
@RequestMapping("/api/v1/context")
@RequiredArgsConstructor
@Slf4j
public class ContextController {

    private final ContextService contextService;

    /**
     * 推送用户上下文
     *
     * 目标系统在用户登录成功后调用此接口
     */
    @PostMapping("/push")
    public ResponseEntity<ApiResponse<PushContextResponse>> pushContext(
            @Valid @RequestBody PushContextRequest request) {

        log.info("Received context push for session: {}, system: {}",
                request.getSessionId(), request.getSystemId());

        contextService.pushContext(request);

        PushContextResponse response = PushContextResponse.builder()
            .sessionId(request.getSessionId())
            .expiresAt(contextService.getSessionExpiresAt(request.getSessionId()))
            .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 获取会话的用户上下文
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<UserContextDTO>> getContext(
            @PathVariable String sessionId) {

        UserContextDTO context = contextService.getContext(sessionId);
        if (context == null) {
            throw new BizException("SESSION_NOT_FOUND",
                "Session not found or expired: " + sessionId);
        }

        // 不返回敏感信息
        context.setAccessToken(null);

        return ResponseEntity.ok(ApiResponse.success(context));
    }

    /**
     * 清除会话的用户上下文
     *
     * 用户登出时调用
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> clearContext(
            @PathVariable String sessionId) {

        contextService.clearContext(sessionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 验证会话是否有效
     */
    @GetMapping("/{sessionId}/valid")
    public ResponseEntity<ApiResponse<SessionValidResponse>> validateSession(
            @PathVariable String sessionId) {

        boolean valid = contextService.isSessionValid(sessionId);
        return ResponseEntity.ok(ApiResponse.success(
            SessionValidResponse.builder()
                .sessionId(sessionId)
                .valid(valid)
                .build()
        ));
    }
}
```

### 4.2 请求/响应 DTO

```java
// 推送上下文请求
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushContextRequest {

    @NotBlank(message = "会话ID不能为空")
    @Size(max = 64, message = "会话ID最长64字符")
    private String sessionId;

    @NotBlank(message = "系统ID不能为空")
    @Size(max = 64, message = "系统ID最长64字符")
    private String systemId;

    @NotNull(message = "用户信息不能为空")
    private UserInfo user;

    private Credentials credentials;

    /**
     * 用户信息
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserInfo {
        @NotBlank(message = "用户ID不能为空")
        private String id;

        @NotBlank(message = "用户名不能为空")
        private String username;

        private List<String> roles;
        private List<String> permissions;
        private String realName;
        private String email;
    }

    /**
     * 认证凭据
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Credentials {
        @NotBlank(message = "访问令牌不能为空")
        private String accessToken;

        private String refreshToken;
        private Long expiresIn; // 秒
    }
}

// 推送上下文响应
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PushContextResponse {
    private String sessionId;
    private LocalDateTime expiresAt;
}

// 会话验证响应
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionValidResponse {
    private String sessionId;
    private Boolean valid;
}
```

---

## 5. 数据库设计

### 5.1 t_session 表

```sql
CREATE TABLE t_session (
    id              BIGINT UNSIGNED     PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    session_id      VARCHAR(64)         NOT NULL COMMENT '会话唯一标识',
    system_id       VARCHAR(64)         NOT NULL COMMENT '目标系统ID',
    user_id         VARCHAR(64)         COMMENT '用户ID',
    username        VARCHAR(128)        COMMENT '用户名',
    user_context    TEXT                COMMENT '用户上下文(JSON)',
    access_token    TEXT                COMMENT '访问令牌(加密)',
    refresh_token   TEXT                COMMENT '刷新令牌(加密)',
    token_expires_at TIMESTAMP          COMMENT '令牌过期时间',
    expires_at      TIMESTAMP           NOT NULL COMMENT '会话过期时间',
    is_deleted      TINYINT             NOT NULL DEFAULT 0 COMMENT '是否已删除',
    created_at      TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_session_id (session_id),
    INDEX idx_system_user (system_id, user_id),
    INDEX idx_expires_at (expires_at),
    INDEX idx_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户会话表';
```

---

## 6. 上下文注入设计

### 6.1 System Prompt 构建

```java
@Service
@RequiredArgsConstructor
public class SystemPromptBuilder {

    private final ContextService contextService;
    private final SystemService systemService;

    /**
     * 构建完整的 System Prompt
     */
    public String buildSystemPrompt(String sessionId) {
        UserContextDTO context = contextService.getContext(sessionId);
        if (context == null) {
            return getBasePrompt();
        }

        StringBuilder prompt = new StringBuilder();
        prompt.append(getBasePrompt()).append("\n\n");
        prompt.append(context.toSystemPromptSection()).append("\n\n");

        // 添加可用工具说明
        String toolsInfo = buildToolsInfo(context);
        prompt.append(toolsInfo);

        return prompt.toString();
    }

    private String getBasePrompt() {
        return """
            你是一个智能助手，帮助用户完成各种操作任务。

            你可以通过调用工具来：
            - 查询和管理目标系统的数据
            - 执行业务操作
            - 搜索网络信息

            请根据用户的需求，选择合适的工具完成任务。
            在执行操作前，请确认用户的意图。
            """;
    }

    private String buildToolsInfo(UserContextDTO context) {
        StringBuilder sb = new StringBuilder();
        sb.append("## 可用工具\n\n");

        // 获取目标系统的工具
        List<ToolDefinition> tools = systemService.getSystemTools(context.getSystemId());

        for (ToolDefinition tool : tools) {
            sb.append("- **").append(tool.getName()).append("**: ")
              .append(tool.getDescription()).append("\n");
        }

        // 添加内置工具
        sb.append("\n### 内置工具\n");
        sb.append("- **web_search**: 搜索网络信息\n");
        sb.append("- **browser_navigate**: 打开网页\n");
        sb.append("- **browser_snapshot**: 获取页面内容\n");

        return sb.toString();
    }
}
```

---

## 7. 接口测试用例

### 7.1 推送用户上下文

```http
POST /api/v1/context/push
Content-Type: application/json

{
  "sessionId": "abc123def456",
  "systemId": "osrm",
  "user": {
    "id": "123",
    "username": "admin",
    "roles": ["ADMIN"],
    "permissions": ["software:create", "subscription:approve"],
    "realName": "管理员",
    "email": "admin@example.com"
  },
  "credentials": {
    "accessToken": "eyJhbG...",
    "refreshToken": "xxx",
    "expiresIn": 7200
  }
}

# 期望响应
{
  "code": 200,
  "message": "success",
  "data": {
    "sessionId": "abc123def456",
    "expiresAt": "2026-03-26T10:00:00"
  }
}
```

### 7.2 获取用户上下文

```http
GET /api/v1/context/abc123def456

# 期望响应
{
  "code": 200,
  "message": "success",
  "data": {
    "sessionId": "abc123def456",
    "systemId": "osrm",
    "userId": "123",
    "username": "admin",
    "roles": ["ADMIN"],
    "permissions": ["software:create", "subscription:approve"],
    "accessToken": null,
    "expiresAt": "2026-03-26T10:00:00"
  }
}
```

### 7.3 验证会话

```http
GET /api/v1/context/abc123def456/valid

# 期望响应
{
  "code": 200,
  "message": "success",
  "data": {
    "sessionId": "abc123def456",
    "valid": true
  }
}
```

### 7.4 清除上下文

```http
DELETE /api/v1/context/abc123def456

# 期望响应
{
  "code": 200,
  "message": "success",
  "data": null
}
```

---

## 8. 错误码定义

| 错误码 | HTTP 状态 | 说明 |
|--------|----------|------|
| SESSION_NOT_FOUND | 404 | 会话不存在或已过期 |
| INVALID_SESSION | 401 | 无效会话 |

---

## 9. 安全考虑

### 9.1 令牌加密

访问令牌在存储前需要加密：

```java
@Service
public class TokenEncryptionService {

    @Value("${app.encryption.key}")
    private String encryptionKey;

    private static final String ALGORITHM = "AES/GCM/NoPadding";

    public String encrypt(String plainText) {
        // AES-GCM 加密实现
        // ...
    }

    public String decrypt(String encryptedText) {
        // AES-GCM 解密实现
        // ...
    }
}
```

### 9.2 日志脱敏

```java
public class SensitiveDataMasker {

    public static String maskToken(String token) {
        if (token == null || token.length() < 10) {
            return "***";
        }
        return token.substring(0, 4) + "..." + token.substring(token.length() - 4);
    }

    public static String maskEmail(String email) {
        if (email == null || !email.contains("@")) {
            return "***";
        }
        int atIndex = email.indexOf("@");
        return email.substring(0, 2) + "***" + email.substring(atIndex);
    }
}

// 日志使用
log.info("Context pushed for session: {}, user: {}, token: {}",
    sessionId, username, SensitiveDataMasker.maskToken(accessToken));
```
