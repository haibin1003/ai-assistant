# 技能系统模块详细设计

## 文档信息

- **模块名称**: 技能系统 (Skill System)
- **版本**: 1.0.0
- **创建日期**: 2026-03-25
- **文档类型**: 模块详细设计文档

---

## 1. 模块概述

### 1.1 功能描述

技能系统模块负责：

- 全局技能管理（管理员创建）
- 私有技能管理（用户创建）
- 技能触发匹配
- 技能执行

### 1.2 技能概念

**技能**是预定义的操作序列模板，帮助用户快速执行常见操作。

```
┌─────────────────────────────────────────────────────────────────────┐
│                         技能系统架构                                  │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│   全局技能 (管理员创建)                                               │
│   ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐    │
│   │ 快速上传软件     │  │ 快速订购软件    │  │ 软件合规检查    │    │
│   │ • 所有用户可用   │  │ • 所有用户可用  │  │ • 所有用户可用  │    │
│   └─────────────────┘  └─────────────────┘  └─────────────────┘    │
│                                                                      │
│   私有技能 (用户创建)                                                 │
│   ┌─────────────────┐  ┌─────────────────┐                          │
│   │ 我的常用搜索     │  │ 审批快捷模板    │                          │
│   │ • 仅自己可用     │  │ • 仅自己可用    │                          │
│   └─────────────────┘  └─────────────────┘                          │
│                                                                      │
│   触发方式:                                                          │
│   1. 显式调用: "/skill upload_software"                             │
│   2. 关键词触发: 用户输入包含 "上传软件"                              │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 1.3 模块位置

```
com.osrm.ai
├── domain.skill             # 领域层
│   ├── entity/
│   │   └── Skill.java
│   └── repository/
│       └── SkillRepository.java
├── application.skill        # 应用服务层
│   ├── SkillService.java
│   ├── SkillMatcher.java
│   └── dto/
│       ├── CreateSkillRequest.java
│       ├── UpdateSkillRequest.java
│       └── SkillDTO.java
└── interfaces.rest          # 接口层
    └── SkillController.java
```

---

## 2. 领域模型

### 2.1 Skill 实体

```java
@Entity
@Table(name = "t_skill")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Skill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 技能唯一标识
     */
    @Column(name = "skill_id", unique = true, nullable = false, length = 64)
    private String skillId;

    /**
     * 技能名称
     */
    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /**
     * 技能描述
     */
    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    /**
     * 提示词模板
     * 使用 {variable} 作为变量占位符
     */
    @Column(name = "prompt_template", nullable = false, columnDefinition = "TEXT")
    private String promptTemplate;

    /**
     * 触发关键词 (JSON 数组)
     */
    @Column(name = "trigger_keywords", columnDefinition = "TEXT")
    private String triggerKeywords;

    /**
     * 所需工具列表 (JSON 数组)
     */
    @Column(name = "required_tools", columnDefinition = "TEXT")
    private String requiredTools;

    /**
     * 是否全局技能
     */
    @Column(name = "is_global", nullable = false)
    @Builder.Default
    private Boolean isGlobal = false;

    /**
     * 是否启用
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private Boolean isActive = true;

    /**
     * 创建人用户ID
     */
    @Column(name = "created_by", length = 64)
    private String createdBy;

    /**
     * 所属系统ID (可选)
     */
    @Column(name = "system_id", length = 64)
    private String systemId;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 获取触发关键词列表
     */
    public List<String> getTriggerKeywordList() {
        if (triggerKeywords == null || triggerKeywords.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return new ObjectMapper().readValue(triggerKeywords, List.class);
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }

    /**
     * 获取所需工具列表
     */
    public List<String> getRequiredToolList() {
        if (requiredTools == null || requiredTools.isEmpty()) {
            return Collections.emptyList();
        }
        try {
            return new ObjectMapper().readValue(requiredTools, List.class);
        } catch (JsonProcessingException e) {
            return Collections.emptyList();
        }
    }
}
```

---

## 3. 服务设计

### 3.1 SkillService

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class SkillService {

    private final SkillRepository skillRepository;
    private final ObjectMapper objectMapper;

    /**
     * 创建技能
     */
    @Transactional
    public SkillDTO createSkill(CreateSkillRequest request, UserContextDTO context) {
        // 验证权限：只有管理员可以创建全局技能
        if (request.getIsGlobal() && !isAdmin(context)) {
            throw new BizException("PERMISSION_DENIED", "Only admin can create global skills");
        }

        // 生成技能ID
        String skillId = generateSkillId(request.getIsGlobal(), context.getUserId());

        // 构建技能实体
        Skill skill = Skill.builder()
            .skillId(skillId)
            .name(request.getName())
            .description(request.getDescription())
            .promptTemplate(request.getPromptTemplate())
            .triggerKeywords(toJson(request.getTriggerKeywords()))
            .requiredTools(toJson(request.getRequiredTools()))
            .isGlobal(request.getIsGlobal() != null ? request.getIsGlobal() : false)
            .isActive(true)
            .createdBy(context != null ? context.getUserId() : null)
            .systemId(request.getSystemId())
            .build();

        skillRepository.save(skill);

        log.info("Skill created: {}, isGlobal: {}", skill.getSkillId(), skill.getIsGlobal());
        return toDTO(skill);
    }

    /**
     * 更新技能
     */
    @Transactional
    public SkillDTO updateSkill(String skillId, UpdateSkillRequest request, UserContextDTO context) {
        Skill skill = skillRepository.findBySkillId(skillId)
            .orElseThrow(() -> new BizException("SKILL_NOT_FOUND", "Skill not found: " + skillId));

        // 验证权限
        if (!canModify(skill, context)) {
            throw new BizException("PERMISSION_DENIED", "You don't have permission to modify this skill");
        }

        // 更新字段
        if (request.getName() != null) {
            skill.setName(request.getName());
        }
        if (request.getDescription() != null) {
            skill.setDescription(request.getDescription());
        }
        if (request.getPromptTemplate() != null) {
            skill.setPromptTemplate(request.getPromptTemplate());
        }
        if (request.getTriggerKeywords() != null) {
            skill.setTriggerKeywords(toJson(request.getTriggerKeywords()));
        }
        if (request.getRequiredTools() != null) {
            skill.setRequiredTools(toJson(request.getRequiredTools()));
        }

        skillRepository.save(skill);

        log.info("Skill updated: {}", skillId);
        return toDTO(skill);
    }

    /**
     * 删除技能
     */
    @Transactional
    public void deleteSkill(String skillId, UserContextDTO context) {
        Skill skill = skillRepository.findBySkillId(skillId)
            .orElseThrow(() -> new BizException("SKILL_NOT_FOUND", "Skill not found: " + skillId));

        // 验证权限
        if (!canModify(skill, context)) {
            throw new BizException("PERMISSION_DENIED", "You don't have permission to delete this skill");
        }

        skill.setIsActive(false);
        skillRepository.save(skill);

        log.info("Skill deleted: {}", skillId);
    }

    /**
     * 获取技能详情
     */
    public SkillDTO getSkill(String skillId) {
        Skill skill = skillRepository.findBySkillId(skillId)
            .orElseThrow(() -> new BizException("SKILL_NOT_FOUND", "Skill not found: " + skillId));
        return toDTO(skill);
    }

    /**
     * 获取可用技能列表
     */
    public List<SkillDTO> getAvailableSkills(UserContextDTO context) {
        List<Skill> skills = new ArrayList<>();

        // 全局技能
        skills.addAll(skillRepository.findByIsGlobalTrueAndIsActiveTrue());

        // 用户私有技能
        if (context != null && context.getUserId() != null) {
            skills.addAll(skillRepository.findByCreatedByAndIsActiveTrue(context.getUserId()));
        }

        return skills.stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * 获取用户创建的技能
     */
    public List<SkillDTO> getUserSkills(String userId) {
        return skillRepository.findByCreatedByAndIsActiveTrue(userId).stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * 获取全局技能列表
     */
    public List<SkillDTO> getGlobalSkills() {
        return skillRepository.findByIsGlobalTrueAndIsActiveTrue().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    // === 私有方法 ===

    private String generateSkillId(boolean isGlobal, String userId) {
        String prefix = isGlobal ? "global" : "user";
        String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 8);
        return prefix + "_" + suffix;
    }

    private boolean isAdmin(UserContextDTO context) {
        return context != null && context.getRoles() != null &&
            context.getRoles().contains("ADMIN");
    }

    private boolean canModify(Skill skill, UserContextDTO context) {
        // 管理员可以修改所有技能
        if (isAdmin(context)) {
            return true;
        }
        // 用户只能修改自己创建的私有技能
        return !skill.getIsGlobal() &&
            skill.getCreatedBy() != null &&
            skill.getCreatedBy().equals(context.getUserId());
    }

    private String toJson(List<String> list) {
        if (list == null || list.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(list);
        } catch (JsonProcessingException e) {
            return null;
        }
    }

    private SkillDTO toDTO(Skill skill) {
        return SkillDTO.builder()
            .skillId(skill.getSkillId())
            .name(skill.getName())
            .description(skill.getDescription())
            .promptTemplate(skill.getPromptTemplate())
            .triggerKeywords(skill.getTriggerKeywordList())
            .requiredTools(skill.getRequiredToolList())
            .isGlobal(skill.getIsGlobal())
            .createdBy(skill.getCreatedBy())
            .systemId(skill.getSystemId())
            .createdAt(skill.getCreatedAt())
            .updatedAt(skill.getUpdatedAt())
            .build();
    }
}
```

### 3.2 SkillMatcher

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class SkillMatcher {

    private final SkillRepository skillRepository;

    /**
     * 匹配技能
     *
     * @param input 用户输入
     * @param context 用户上下文
     * @return 匹配的技能，如果没有匹配返回 null
     */
    public Skill matchSkill(String input, UserContextDTO context) {
        if (input == null || input.isEmpty()) {
            return null;
        }

        // 1. 检查显式调用
        Skill explicitSkill = matchExplicitCall(input);
        if (explicitSkill != null) {
            return explicitSkill;
        }

        // 2. 检查关键词触发
        return matchKeywords(input, context);
    }

    /**
     * 匹配显式调用
     * 格式: /skill {skillId} 或 /技能 {skillId}
     */
    private Skill matchExplicitCall(String input) {
        String trimmedInput = input.trim();

        // 匹配 /skill xxx 或 /技能 xxx
        Pattern pattern = Pattern.compile("^/(skill|技能)\\s+(\\S+)");
        Matcher matcher = pattern.matcher(trimmedInput);

        if (matcher.find()) {
            String skillId = matcher.group(2);
            return skillRepository.findBySkillIdAndIsActiveTrue(skillId).orElse(null);
        }

        return null;
    }

    /**
     * 匹配关键词触发
     */
    private Skill matchKeywords(String input, UserContextDTO context) {
        // 获取可用技能
        List<Skill> availableSkills = getAvailableSkills(context);

        // 按匹配优先级排序（全局技能优先）
        availableSkills.sort((a, b) -> {
            if (a.getIsGlobal() != b.getIsGlobal()) {
                return a.getIsGlobal() ? -1 : 1;
            }
            return 0;
        });

        // 遍历检查关键词匹配
        for (Skill skill : availableSkills) {
            List<String> keywords = skill.getTriggerKeywordList();
            if (keywords != null) {
                for (String keyword : keywords) {
                    if (input.contains(keyword)) {
                        log.info("Skill matched by keyword: {} -> {}", keyword, skill.getSkillId());
                        return skill;
                    }
                }
            }
        }

        return null;
    }

    /**
     * 构建技能提示词
     *
     * @param skill 技能
     * @param variables 变量映射
     * @return 渲染后的提示词
     */
    public String buildPrompt(Skill skill, Map<String, String> variables) {
        String template = skill.getPromptTemplate();

        if (variables == null || variables.isEmpty()) {
            return template;
        }

        // 替换变量 {variable}
        String result = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            result = result.replace("{" + entry.getKey() + "}", entry.getValue());
        }

        return result;
    }

    private List<Skill> getAvailableSkills(UserContextDTO context) {
        List<Skill> skills = new ArrayList<>();

        // 全局技能
        skills.addAll(skillRepository.findByIsGlobalTrueAndIsActiveTrue());

        // 用户私有技能
        if (context != null && context.getUserId() != null) {
            skills.addAll(skillRepository.findByCreatedByAndIsActiveTrue(context.getUserId()));
        }

        return skills;
    }
}
```

---

## 4. REST 接口设计

### 4.1 SkillController

```java
@RestController
@RequestMapping("/api/v1/skills")
@RequiredArgsConstructor
@Slf4j
public class SkillController {

    private final SkillService skillService;

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
```

### 4.2 请求/响应 DTO

```java
// 创建技能请求
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateSkillRequest {

    @NotBlank(message = "技能名称不能为空")
    @Size(max = 128, message = "技能名称最长128字符")
    private String name;

    @NotBlank(message = "技能描述不能为空")
    private String description;

    @NotBlank(message = "提示词模板不能为空")
    private String promptTemplate;

    private List<String> triggerKeywords;

    private List<String> requiredTools;

    private Boolean isGlobal;

    private String systemId;
}

// 更新技能请求
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateSkillRequest {

    @Size(max = 128, message = "技能名称最长128字符")
    private String name;

    private String description;

    private String promptTemplate;

    private List<String> triggerKeywords;

    private List<String> requiredTools;
}

// 技能 DTO
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SkillDTO {

    private String skillId;
    private String name;
    private String description;
    private String promptTemplate;
    private List<String> triggerKeywords;
    private List<String> requiredTools;
    private Boolean isGlobal;
    private String createdBy;
    private String systemId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
```

---

## 5. 数据库设计

### 5.1 t_skill 表

```sql
CREATE TABLE t_skill (
    id              BIGINT UNSIGNED     PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    skill_id        VARCHAR(64)         NOT NULL COMMENT '技能唯一标识',
    name            VARCHAR(128)        NOT NULL COMMENT '技能名称',
    description     TEXT                NOT NULL COMMENT '技能描述',
    prompt_template TEXT                NOT NULL COMMENT '提示词模板',
    trigger_keywords TEXT               COMMENT '触发关键词(JSON数组)',
    required_tools  TEXT                COMMENT '所需工具(JSON数组)',
    is_global       TINYINT             NOT NULL DEFAULT 0 COMMENT '是否全局',
    is_active       TINYINT             NOT NULL DEFAULT 1 COMMENT '是否启用',
    created_by      VARCHAR(64)         COMMENT '创建人用户ID',
    system_id       VARCHAR(64)         COMMENT '所属系统ID',
    created_at      TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_skill_id (skill_id),
    INDEX idx_is_global (is_global),
    INDEX idx_created_by (created_by),
    INDEX idx_is_active (is_active)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='技能定义表';
```

---

## 6. 技能示例

### 6.1 快速上传软件

```json
{
  "skillId": "global_upload_software",
  "name": "快速上传软件",
  "description": "一键创建软件包、添加版本、提交审核",
  "promptTemplate": "帮助用户上传软件包。软件名称: {name}, 版本: {version}, 描述: {description}。请执行以下步骤：\n1. 检查软件是否已存在\n2. 如果不存在，创建软件包\n3. 添加版本信息\n4. 提交审核",
  "triggerKeywords": ["上传软件", "新增软件", "添加软件"],
  "requiredTools": ["osrm_search_software", "osrm_create_package", "osrm_add_version"],
  "isGlobal": true
}
```

### 6.2 快速订购软件

```json
{
  "skillId": "global_quick_subscribe",
  "name": "快速订购软件",
  "description": "快速创建软件订购申请",
  "promptTemplate": "帮助用户订购软件。软件名称: {softwareName}, 业务系统: {businessSystem}, 使用场景: {scenario}。请执行以下步骤：\n1. 搜索软件\n2. 创建订购申请\n3. 提交审批",
  "triggerKeywords": ["订购软件", "申请使用软件"],
  "requiredTools": ["osrm_search_software", "osrm_create_subscription"],
  "isGlobal": true
}
```

### 6.3 用户私有技能

```json
{
  "skillId": "user_abc12345",
  "name": "我的常用搜索模板",
  "description": "个人常用的软件搜索模板",
  "promptTemplate": "帮我搜索 {keyword} 相关的软件，重点关注: {focus}",
  "triggerKeywords": ["我的搜索"],
  "requiredTools": ["osrm_search_software"],
  "isGlobal": false,
  "createdBy": "user123"
}
```

---

## 7. 接口测试用例

### 7.1 创建技能

```http
POST /api/v1/skills
Content-Type: application/json
X-Session-Id: abc123def456

{
  "name": "快速上传软件",
  "description": "一键创建软件包、添加版本、提交审核",
  "promptTemplate": "帮助用户上传软件包...",
  "triggerKeywords": ["上传软件", "新增软件"],
  "requiredTools": ["osrm_search_software", "osrm_create_package"],
  "isGlobal": false
}

# 期望响应
{
  "code": 200,
  "message": "success",
  "data": {
    "skillId": "user_abc12345",
    "name": "快速上传软件",
    "description": "一键创建软件包、添加版本、提交审核",
    "isGlobal": false,
    "createdBy": "123",
    "createdAt": "2026-03-25T10:00:00"
  }
}
```

### 7.2 获取可用技能

```http
GET /api/v1/skills
X-Session-Id: abc123def456

# 期望响应
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "skillId": "global_upload_software",
      "name": "快速上传软件",
      "isGlobal": true
    },
    {
      "skillId": "user_abc12345",
      "name": "我的常用搜索模板",
      "isGlobal": false
    }
  ]
}
```

### 7.3 显式调用技能

```http
POST /api/v1/chat
X-Session-Id: abc123def456

{
  "content": "/skill global_upload_software"
}

# AI 将使用技能的 promptTemplate 作为初始指令
```

---

## 8. 错误码定义

| 错误码 | HTTP 状态 | 说明 |
|--------|----------|------|
| SKILL_NOT_FOUND | 404 | 技能不存在 |
| PERMISSION_DENIED | 403 | 无权限操作 |
| SESSION_INVALID | 401 | 会话无效 |

---

## 9. 与对话引擎集成

### 9.1 在 ChatService 中集成

```java
@Service
public class ChatService {

    private final SkillMatcher skillMatcher;
    // ...

    private void processChatStream(String sessionId, ChatRequest request,
                                    FluxSink<ServerSentEvent<ChatEvent>> emitter) {
        // ...

        // 检查是否触发技能
        Skill matchedSkill = skillMatcher.matchSkill(request.getContent(), context);
        if (matchedSkill != null) {
            // 使用技能模板作为初始提示词
            String skillPrompt = skillMatcher.buildPrompt(matchedSkill, Collections.emptyMap());
            // 在对话中注入技能提示词
            // ...
        }

        // ...
    }
}
```
