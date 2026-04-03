# 配置管理模块详细设计

## 文档信息

- **模块名称**: 配置管理 (Configuration Management)
- **版本**: 1.0.0
- **创建日期**: 2026-03-25
- **文档类型**: 模块详细设计文档

---

## 1. 模块概述

### 1.1 功能描述

配置管理模块负责：

- LLM API Key 配置（支持多提供商）
- 搜索服务 API Key 配置
- 系统全局配置
- 敏感信息加密存储

### 1.2 模块位置

```
com.osrm.ai
├── domain.config            # 领域层
│   ├── entity/
│   │   └── ApiKeyConfig.java
│   └── repository/
│       └── ApiKeyConfigRepository.java
├── application.config       # 应用服务层
│   ├── ConfigService.java
│   └── dto/
│       ├── ApiKeyConfigDTO.java
│       └── SetApiKeyRequest.java
├── infrastructure.security  # 基础设施层
│   └── EncryptionService.java
└── interfaces.rest          # 接口层
    └── ConfigController.java
```

---

## 2. 领域模型

### 2.1 ApiKeyConfig 实体

```java
@Entity
@Table(name = "t_api_key_config")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyConfig {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 提供商标识
     * llm: deepseek, openai, claude
     * search: serper, tavily
     */
    @Column(name = "provider", unique = true, nullable = false, length = 32)
    private String provider;

    /**
     * 提供商类型
     */
    @Column(name = "provider_type", nullable = false, length = 32)
    private String providerType; // llm, search

    /**
     * API Key (加密存储)
     */
    @Column(name = "api_key", columnDefinition = "TEXT")
    private String apiKey;

    /**
     * API 端点 (可选覆盖)
     */
    @Column(name = "api_endpoint", length = 512)
    private String apiEndpoint;

    /**
     * 是否启用
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 配置备注
     */
    @Column(name = "remark", length = 256)
    private String remark;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;
}
```

---

## 3. 服务设计

### 3.1 ConfigService

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigService {

    private final ApiKeyConfigRepository apiKeyConfigRepository;
    private final EncryptionService encryptionService;

    // 缓存 API Key (内存)
    private final Map<String, String> apiKeyCache = new ConcurrentHashMap<>();

    /**
     * 设置 API Key
     */
    @Transactional
    public void setApiKey(String provider, String providerType, String apiKey, String apiEndpoint) {
        Optional<ApiKeyConfig> existing = apiKeyConfigRepository.findByProvider(provider);

        String encryptedKey = encryptionService.encrypt(apiKey);

        ApiKeyConfig config;
        if (existing.isPresent()) {
            config = existing.get();
            config.setApiKey(encryptedKey);
            if (apiEndpoint != null) {
                config.setApiEndpoint(apiEndpoint);
            }
            config.setIsActive(true);
        } else {
            config = ApiKeyConfig.builder()
                .provider(provider)
                .providerType(providerType)
                .apiKey(encryptedKey)
                .apiEndpoint(apiEndpoint)
                .isActive(true)
                .build();
        }

        apiKeyConfigRepository.save(config);

        // 更新缓存
        apiKeyCache.put(provider, apiKey);

        log.info("API Key configured for provider: {}", provider);
    }

    /**
     * 获取 API Key (解密后)
     */
    public String getApiKey(String provider) {
        // 先查缓存
        String cached = apiKeyCache.get(provider);
        if (cached != null) {
            return cached;
        }

        // 查数据库
        Optional<ApiKeyConfig> config = apiKeyConfigRepository.findByProviderAndIsActiveTrue(provider);
        if (config.isEmpty()) {
            return null;
        }

        String decryptedKey = encryptionService.decrypt(config.get().getApiKey());

        // 更新缓存
        apiKeyCache.put(provider, decryptedKey);

        return decryptedKey;
    }

    /**
     * 获取 API Key 配置状态
     */
    public ApiKeyConfigDTO getApiKeyConfig(String provider) {
        Optional<ApiKeyConfig> config = apiKeyConfigRepository.findByProvider(provider);
        if (config.isEmpty()) {
            return ApiKeyConfigDTO.builder()
                .provider(provider)
                .configured(false)
                .build();
        }

        return ApiKeyConfigDTO.builder()
            .provider(provider)
            .providerType(config.get().getProviderType())
            .configured(true)
            .active(config.get().getIsActive())
            .apiEndpoint(config.get().getApiEndpoint())
            .createdAt(config.get().getCreatedAt())
            .updatedAt(config.get().getUpdatedAt())
            // 不返回实际的 API Key
            .build();
    }

    /**
     * 获取所有 API Key 配置状态
     */
    public List<ApiKeyConfigDTO> getAllApiKeyConfigs() {
        List<ApiKeyConfig> configs = apiKeyConfigRepository.findAll();

        return configs.stream()
            .map(config -> ApiKeyConfigDTO.builder()
                .provider(config.getProvider())
                .providerType(config.getProviderType())
                .configured(true)
                .active(config.getIsActive())
                .apiEndpoint(config.getApiEndpoint())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build())
            .collect(Collectors.toList());
    }

    /**
     * 删除 API Key 配置
     */
    @Transactional
    public void deleteApiKey(String provider) {
        apiKeyConfigRepository.findByProvider(provider).ifPresent(config -> {
            config.setIsActive(false);
            config.setApiKey(null);
            apiKeyConfigRepository.save(config);
        });

        apiKeyCache.remove(provider);

        log.info("API Key deleted for provider: {}", provider);
    }

    /**
     * 获取当前 LLM 提供商
     */
    public String getLLMProvider() {
        // 默认使用 deepseek
        String provider = "deepseek";

        // 检查是否配置了 API Key
        if (getApiKey(provider) != null) {
            return provider;
        }

        // 尝试其他提供商
        String[] providers = {"openai", "claude"};
        for (String p : providers) {
            if (getApiKey(p) != null) {
                return p;
            }
        }

        return provider;
    }

    /**
     * 获取当前搜索提供商
     */
    public String getSearchProvider() {
        String provider = "serper";

        if (getApiKey(provider) != null) {
            return provider;
        }

        if (getApiKey("tavily") != null) {
            return "tavily";
        }

        return provider;
    }

    /**
     * 验证 API Key 是否有效
     */
    public boolean validateApiKey(String provider, String providerType) {
        String apiKey = getApiKey(provider);
        if (apiKey == null || apiKey.isEmpty()) {
            return false;
        }

        // TODO: 实际调用 API 验证
        return true;
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        apiKeyCache.clear();
        log.info("API Key cache cleared");
    }
}
```

### 3.2 EncryptionService

```java
@Service
@Slf4j
public class EncryptionService {

    @Value("${app.encryption.key:default-encryption-key}")
    private String encryptionKey;

    private static final String ALGORITHM = "AES/GCM/NoPadding";
    private static final int GCM_IV_LENGTH = 12;
    private static final int GCM_TAG_LENGTH = 128;

    /**
     * 加密
     */
    public String encrypt(String plainText) {
        if (plainText == null || plainText.isEmpty()) {
            return plainText;
        }

        try {
            // 生成随机 IV
            byte[] iv = new byte[GCM_IV_LENGTH];
            SecureRandom random = new SecureRandom();
            random.nextBytes(iv);

            // 初始化密钥
            SecretKeySpec keySpec = new SecretKeySpec(
                deriveKey(encryptionKey), "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            // 加密
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.ENCRYPT_MODE, keySpec, gcmSpec);
            byte[] encrypted = cipher.doFinal(plainText.getBytes(StandardCharsets.UTF_8));

            // 组合 IV + 加密数据
            ByteBuffer buffer = ByteBuffer.allocate(iv.length + encrypted.length);
            buffer.put(iv);
            buffer.put(encrypted);

            return Base64.getEncoder().encodeToString(buffer.array());
        } catch (Exception e) {
            log.error("Encryption failed", e);
            throw new EncryptionException("Encryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * 解密
     */
    public String decrypt(String encryptedText) {
        if (encryptedText == null || encryptedText.isEmpty()) {
            return encryptedText;
        }

        try {
            byte[] decoded = Base64.getDecoder().decode(encryptedText);

            // 分离 IV 和加密数据
            ByteBuffer buffer = ByteBuffer.wrap(decoded);
            byte[] iv = new byte[GCM_IV_LENGTH];
            buffer.get(iv);
            byte[] encrypted = new byte[buffer.remaining()];
            buffer.get(encrypted);

            // 初始化密钥
            SecretKeySpec keySpec = new SecretKeySpec(
                deriveKey(encryptionKey), "AES");
            GCMParameterSpec gcmSpec = new GCMParameterSpec(GCM_TAG_LENGTH, iv);

            // 解密
            Cipher cipher = Cipher.getInstance(ALGORITHM);
            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmSpec);
            byte[] decrypted = cipher.doFinal(encrypted);

            return new String(decrypted, StandardCharsets.UTF_8);
        } catch (Exception e) {
            log.error("Decryption failed", e);
            throw new EncryptionException("Decryption failed: " + e.getMessage(), e);
        }
    }

    /**
     * 从密码派生密钥
     */
    private byte[] deriveKey(String password) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return digest.digest(password.getBytes(StandardCharsets.UTF_8));
    }
}

// 加密异常
public class EncryptionException extends RuntimeException {
    public EncryptionException(String message, Throwable cause) {
        super(message, cause);
    }
}
```

---

## 4. REST 接口设计

### 4.1 ConfigController

```java
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
@Slf4j
public class ConfigController {

    private final ConfigService configService;

    /**
     * 获取 API Key 配置状态
     */
    @GetMapping("/api-key")
    public ResponseEntity<ApiResponse<List<ApiKeyConfigDTO>>> getApiKeyConfigs() {
        List<ApiKeyConfigDTO> configs = configService.getAllApiKeyConfigs();
        return ResponseEntity.ok(ApiResponse.success(configs));
    }

    /**
     * 获取单个提供商的配置状态
     */
    @GetMapping("/api-key/{provider}")
    public ResponseEntity<ApiResponse<ApiKeyConfigDTO>> getApiKeyConfig(
            @PathVariable String provider) {
        ApiKeyConfigDTO config = configService.getApiKeyConfig(provider);
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    /**
     * 设置 API Key
     */
    @PutMapping("/api-key/{provider}")
    public ResponseEntity<ApiResponse<Void>> setApiKey(
            @PathVariable String provider,
            @Valid @RequestBody SetApiKeyRequest request) {

        configService.setApiKey(
            provider,
            request.getProviderType(),
            request.getApiKey(),
            request.getApiEndpoint()
        );

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 删除 API Key
     */
    @DeleteMapping("/api-key/{provider}")
    public ResponseEntity<ApiResponse<Void>> deleteApiKey(
            @PathVariable String provider) {

        configService.deleteApiKey(provider);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 验证 API Key
     */
    @PostMapping("/api-key/{provider}/validate")
    public ResponseEntity<ApiResponse<ValidationResult>> validateApiKey(
            @PathVariable String provider,
            @RequestParam String providerType) {

        boolean valid = configService.validateApiKey(provider, providerType);

        return ResponseEntity.ok(ApiResponse.success(
            ValidationResult.builder()
                .provider(provider)
                .valid(valid)
                .build()
        ));
    }
}
```

### 4.2 请求/响应 DTO

```java
// 设置 API Key 请求
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SetApiKeyRequest {

    @NotBlank(message = "提供商类型不能为空")
    private String providerType; // llm, search

    @NotBlank(message = "API Key 不能为空")
    private String apiKey;

    private String apiEndpoint;
}

// API Key 配置 DTO
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyConfigDTO {

    private String provider;
    private String providerType;
    private Boolean configured;
    private Boolean active;
    private String apiEndpoint;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

// 验证结果
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ValidationResult {
    private String provider;
    private Boolean valid;
    private String message;
}
```

---

## 5. 数据库设计

### 5.1 t_api_key_config 表

```sql
CREATE TABLE t_api_key_config (
    id              BIGINT UNSIGNED     PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    provider        VARCHAR(32)         NOT NULL COMMENT '提供商标识',
    provider_type   VARCHAR(32)         NOT NULL COMMENT '提供商类型: llm/search',
    api_key         TEXT                COMMENT 'API Key(加密)',
    api_endpoint    VARCHAR(512)        COMMENT 'API 端点(可选覆盖)',
    is_active       TINYINT             NOT NULL DEFAULT 1 COMMENT '是否启用',
    remark          VARCHAR(256)        COMMENT '配置备注',
    created_at      TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_provider (provider),
    INDEX idx_provider_type (provider_type)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='API Key配置表';
```

---

## 6. 配置示例

### 6.1 application.yml

```yaml
# 应用配置
app:
  encryption:
    key: ${ENCRYPTION_KEY:change-this-in-production}

# LLM 配置
llm:
  default-provider: deepseek
  deepseek:
    model: deepseek-chat
    timeout: 120000
  openai:
    model: gpt-4
    timeout: 120000
  claude:
    model: claude-3-sonnet
    timeout: 120000

# 搜索配置
search:
  default-provider: serper
  serper:
    timeout: 30000
  tavily:
    timeout: 30000

# 浏览器工具配置
browser:
  enabled: false
  playwright-url: ${PLAYWRIGHT_URL:}
```

### 6.2 配置 API Key

```http
# 配置 DeepSeek API Key
PUT /api/v1/config/api-key/deepseek
Content-Type: application/json

{
  "providerType": "llm",
  "apiKey": "sk-xxxxxxxxxxxxxxxx"
}

# 配置 Serper API Key
PUT /api/v1/config/api-key/serper
Content-Type: application/json

{
  "providerType": "search",
  "apiKey": "xxxxxxxxxxxxxxxx"
}
```

---

## 7. 接口测试用例

### 7.1 设置 API Key

```http
PUT /api/v1/config/api-key/deepseek
Content-Type: application/json

{
  "providerType": "llm",
  "apiKey": "sk-test-key-12345"
}

# 期望响应
{
  "code": 200,
  "message": "success",
  "data": null
}
```

### 7.2 获取配置状态

```http
GET /api/v1/config/api-key

# 期望响应
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "provider": "deepseek",
      "providerType": "llm",
      "configured": true,
      "active": true,
      "createdAt": "2026-03-25T10:00:00"
    }
  ]
}
```

### 7.3 验证 API Key

```http
POST /api/v1/config/api-key/deepseek/validate?providerType=llm

# 期望响应
{
  "code": 200,
  "message": "success",
  "data": {
    "provider": "deepseek",
    "valid": true
  }
}
```

---

## 8. 错误码定义

| 错误码 | HTTP 状态 | 说明 |
|--------|----------|------|
| ENCRYPTION_ERROR | 500 | 加密/解密失败 |
| INVALID_API_KEY | 400 | 无效的 API Key |
| PROVIDER_NOT_FOUND | 404 | 提供商不存在 |

---

## 9. 安全考虑

### 9.1 API Key 安全

1. **加密存储**: 所有 API Key 使用 AES-GCM 加密存储
2. **内存缓存**: 敏感信息在内存中以明文缓存，重启后需重新加载
3. **日志脱敏**: 日志中不记录完整的 API Key

### 9.2 访问控制

```java
// 管理接口需要管理员权限
@PreAuthorize("hasRole('ADMIN')")
@PutMapping("/api-key/{provider}")
public ResponseEntity<ApiResponse<Void>> setApiKey(...) {
    // ...
}
```

### 9.3 加密密钥管理

- 生产环境必须设置 `ENCRYPTION_KEY` 环境变量
- 密钥长度建议 32 字符以上
- 定期轮换加密密钥

```bash
# 生成加密密钥
openssl rand -base64 32

# 设置环境变量
export ENCRYPTION_KEY="your-generated-key"
```
