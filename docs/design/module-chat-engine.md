# 对话引擎模块详细设计

## 文档信息

- **模块名称**: 对话引擎 (Chat Engine)
- **版本**: 1.0.0
- **创建日期**: 2026-03-25
- **文档类型**: 模块详细设计文档

---

## 1. 模块概述

### 1.1 功能描述

对话引擎模块是 AI 助手的核心，负责：

- 流式对话处理 (SSE)
- 多模型支持 (DeepSeek/OpenAI/Claude)
- 工具调用循环
- 上下文注入
- 对话历史管理

### 1.2 模块位置

```
com.osrm.ai
├── domain.conversation      # 领域层
│   ├── entity/
│   │   ├── Conversation.java
│   │   └── Message.java
│   └── repository/
│       ├── ConversationRepository.java
│       └── MessageRepository.java
├── application.chat         # 应用服务层
│   ├── ChatService.java
│   ├── ConversationService.java
│   ├── SystemPromptBuilder.java
│   └── dto/
│       ├── ChatRequest.java
│       ├── ChatEvent.java
│       └── ToolCallContext.java
├── infrastructure.llm       # 基础设施层
│   ├── LLMClient.java
│   ├── DeepSeekClient.java
│   ├── OpenAIClient.java
│   └── ClaudeClient.java
└── interfaces.rest          # 接口层
    └── ChatController.java
```

---

## 2. 领域模型

### 2.1 Conversation 实体

```java
@Entity
@Table(name = "t_conversation")
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 对话唯一标识
     */
    @Column(name = "conversation_id", unique = true, nullable = false, length = 64)
    private String conversationId;

    /**
     * 所属会话ID
     */
    @Column(name = "session_id", nullable = false, length = 64)
    private String sessionId;

    /**
     * 对话标题
     */
    @Column(name = "title", length = 256)
    private String title;

    /**
     * 消息数量
     */
    @Column(name = "message_count", nullable = false)
    @Builder.Default
    private Integer messageCount = 0;

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
}
```

### 2.2 Message 实体

```java
@Entity
@Table(name = "t_message")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Message {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 消息唯一标识
     */
    @Column(name = "message_id", unique = true, nullable = false, length = 64)
    private String messageId;

    /**
     * 所属对话ID
     */
    @Column(name = "conversation_id", nullable = false, length = 64)
    private String conversationId;

    /**
     * 角色: user / assistant / tool
     */
    @Column(name = "role", nullable = false, length = 32)
    private String role;

    /**
     * 消息内容
     */
    @Column(name = "content", columnDefinition = "TEXT")
    private String content;

    /**
     * 工具调用 (JSON)
     */
    @Column(name = "tool_calls", columnDefinition = "TEXT")
    private String toolCalls;

    /**
     * 工具调用ID
     */
    @Column(name = "tool_call_id", length = 64)
    private String toolCallId;

    /**
     * 工具名称
     */
    @Column(name = "tool_name", length = 128)
    private String toolName;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        if (messageId == null) {
            messageId = UUID.randomUUID().toString().replace("-", "");
        }
    }
}
```

### 2.3 ChatEvent 值对象

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatEvent {

    /**
     * 事件类型
     */
    private String event;

    /**
     * 事件数据
     */
    private Object data;

    // 事件类型常量
    public static final String EVENT_CONTENT = "content";
    public static final String EVENT_TOOL_USE = "tool_use";
    public static final String EVENT_TOOL_RESULT = "tool_result";
    public static final String EVENT_ERROR = "error";
    public static final String EVENT_DONE = "done";

    public static ChatEvent content(String content) {
        return ChatEvent.builder()
            .event(EVENT_CONTENT)
            .data(Map.of("content", content))
            .build();
    }

    public static ChatEvent toolUse(String tool, Map<String, Object> args) {
        return ChatEvent.builder()
            .event(EVENT_TOOL_USE)
            .data(Map.of("tool", tool, "args", args))
            .build();
    }

    public static ChatEvent toolResult(boolean success, Object result) {
        return ChatEvent.builder()
            .event(EVENT_TOOL_RESULT)
            .data(Map.of("success", success, "data", result))
            .build();
    }

    public static ChatEvent error(String code, String message) {
        return ChatEvent.builder()
            .event(EVENT_ERROR)
            .data(Map.of("code", code, "message", message))
            .build();
    }

    public static ChatEvent done() {
        return ChatEvent.builder()
            .event(EVENT_DONE)
            .data(Map.of())
            .build();
    }
}
```

---

## 3. 服务设计

### 3.1 ChatService

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ChatService {

    private final ContextService contextService;
    private final ConversationService conversationService;
    private final SystemPromptBuilder systemPromptBuilder;
    private final ToolRouter toolRouter;
    private final LLMClientFactory llmClientFactory;
    private final ConfigService configService;

    // 最大工具调用轮次
    private static final int MAX_TOOL_ROUNDS = 10;

    /**
     * 流式对话
     */
    public Flux<ServerSentEvent<ChatEvent>> chatStream(String sessionId, ChatRequest request) {
        return Flux.create(emitter -> {
            try {
                processChatStream(sessionId, request, emitter);
            } catch (Exception e) {
                log.error("Chat stream error", e);
                emitter.next(ServerSentEvent.builder(ChatEvent.error("CHAT_ERROR", e.getMessage())).build());
                emitter.complete();
            }
        });
    }

    private void processChatStream(String sessionId, ChatRequest request,
                                    FluxSink<ServerSentEvent<ChatEvent>> emitter) {
        // 1. 获取用户上下文
        UserContextDTO context = contextService.getContext(sessionId);
        if (context == null) {
            emitter.next(ServerSentEvent.builder(
                ChatEvent.error("SESSION_INVALID", "会话无效或已过期")).build());
            emitter.complete();
            return;
        }

        // 2. 获取或创建对话
        Conversation conversation = conversationService.getOrCreateConversation(
            sessionId, request.getConversationId());

        // 3. 保存用户消息
        conversationService.addMessage(conversation.getConversationId(),
            "user", request.getContent());

        // 4. 构建 System Prompt
        String systemPrompt = systemPromptBuilder.buildSystemPrompt(sessionId);

        // 5. 获取历史消息
        List<Message> history = conversationService.getRecentMessages(
            conversation.getConversationId(), 20);

        // 6. 获取可用工具
        List<ToolDefinition> tools = getAvailableTools(context);

        // 7. 获取 LLM 客户端
        LLMClient llmClient = llmClientFactory.createClient(
            configService.getLLMProvider());

        // 8. 开始对话循环
        processDialogLoop(llmClient, systemPrompt, history, request.getContent(),
            tools, context, emitter);

        // 9. 完成
        emitter.next(ServerSentEvent.builder(ChatEvent.done()).build());
        emitter.complete();
    }

    private void processDialogLoop(LLMClient llmClient, String systemPrompt,
                                     List<Message> history, String userContent,
                                     List<ToolDefinition> tools, UserContextDTO context,
                                     FluxSink<ServerSentEvent<ChatEvent>> emitter) {

        List<Message> dialogHistory = new ArrayList<>(history);
        dialogHistory.add(createUserMessage(userContent));

        int toolRounds = 0;

        while (toolRounds < MAX_TOOL_ROUNDS) {
            // 调用 LLM
            LLMResponse response = llmClient.chat(systemPrompt, dialogHistory, tools);

            // 处理响应
            if (response.hasContent()) {
                // 输出内容
                emitter.next(ServerSentEvent.builder(ChatEvent.content(response.getContent())).build());

                // 保存助手消息
                // conversationService.addMessage(conversationId, "assistant", response.getContent());
            }

            if (response.hasToolCalls()) {
                // 处理工具调用
                List<ToolCall> toolCalls = response.getToolCalls();

                for (ToolCall toolCall : toolCalls) {
                    // 通知工具调用
                    emitter.next(ServerSentEvent.builder(
                        ChatEvent.toolUse(toolCall.getName(), toolCall.getArguments())).build());

                    // 执行工具
                    ToolResult result = executeToolCall(toolCall, context);

                    // 通知结果
                    emitter.next(ServerSentEvent.builder(
                        ChatEvent.toolResult(result.isSuccess(), result.getData())).build());

                    // 添加到历史
                    dialogHistory.add(createAssistantMessage(toolCall));
                    dialogHistory.add(createToolMessage(toolCall.getId(), result));
                }

                toolRounds++;
            } else {
                // 没有工具调用，结束循环
                break;
            }
        }

        if (toolRounds >= MAX_TOOL_ROUNDS) {
            log.warn("Max tool rounds reached");
            emitter.next(ServerSentEvent.builder(
                ChatEvent.error("MAX_ROUNDS", "达到最大工具调用轮次")).build());
        }
    }

    private ToolResult executeToolCall(ToolCall toolCall, UserContextDTO context) {
        try {
            return toolRouter.execute(toolCall.getName(), toolCall.getArguments(), context);
        } catch (Exception e) {
            log.error("Tool call failed: {}", toolCall.getName(), e);
            return ToolResult.failure(e.getMessage());
        }
    }

    private List<ToolDefinition> getAvailableTools(UserContextDTO context) {
        // 获取目标系统工具 + 内置工具
        List<ToolDefinition> tools = new ArrayList<>();

        // 系统工具
        tools.addAll(systemService.getSystemTools(context.getSystemId()));

        // 内置工具
        tools.addAll(getBuiltInTools());

        return tools;
    }

    private List<ToolDefinition> getBuiltInTools() {
        return List.of(
            ToolDefinition.builder()
                .name("web_search")
                .description("搜索网络信息")
                .inputSchema(buildWebSearchSchema())
                .build(),
            ToolDefinition.builder()
                .name("browser_navigate")
                .description("打开网页")
                .inputSchema(buildBrowserNavigateSchema())
                .build(),
            ToolDefinition.builder()
                .name("browser_snapshot")
                .description("获取页面内容快照")
                .inputSchema(buildBrowserSnapshotSchema())
                .build()
        );
    }
}
```

### 3.2 ConversationService

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final MessageRepository messageRepository;

    /**
     * 获取或创建对话
     */
    @Transactional
    public Conversation getOrCreateConversation(String sessionId, String conversationId) {
        if (conversationId != null) {
            return conversationRepository.findByConversationId(conversationId)
                .orElseGet(() -> createConversation(sessionId));
        }
        return createConversation(sessionId);
    }

    /**
     * 创建新对话
     */
    @Transactional
    public Conversation createConversation(String sessionId) {
        Conversation conversation = Conversation.builder()
            .conversationId(generateConversationId())
            .sessionId(sessionId)
            .messageCount(0)
            .isDeleted(false)
            .build();

        return conversationRepository.save(conversation);
    }

    /**
     * 添加消息
     */
    @Transactional
    public Message addMessage(String conversationId, String role, String content) {
        Message message = Message.builder()
            .messageId(generateMessageId())
            .conversationId(conversationId)
            .role(role)
            .content(content)
            .build();

        messageRepository.save(message);

        // 更新消息计数
        conversationRepository.incrementMessageCount(conversationId);

        return message;
    }

    /**
     * 获取最近消息
     */
    public List<Message> getRecentMessages(String conversationId, int limit) {
        return messageRepository.findTopNByConversationIdOrderByCreatedAtDesc(
            conversationId, PageRequest.of(0, limit));
    }

    /**
     * 获取用户的对话列表
     */
    public List<Conversation> getUserConversations(String sessionId) {
        return conversationRepository.findBySessionIdAndIsDeletedFalseOrderByUpdatedAtDesc(sessionId);
    }

    /**
     * 删除对话
     */
    @Transactional
    public void deleteConversation(String conversationId) {
        conversationRepository.findByConversationId(conversationId).ifPresent(conv -> {
            conv.setIsDeleted(true);
            conversationRepository.save(conv);
        });
    }

    private String generateConversationId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    private String generateMessageId() {
        return UUID.randomUUID().toString().replace("-", "");
    }
}
```

---

## 4. LLM 客户端设计

### 4.1 LLMClient 接口

```java
public interface LLMClient {

    /**
     * 同步对话
     */
    LLMResponse chat(String systemPrompt, List<Message> history, List<ToolDefinition> tools);

    /**
     * 流式对话
     */
    Flux<LLMChunk> streamChat(String systemPrompt, List<Message> history, List<ToolDefinition> tools);
}
```

### 4.2 LLM 数据结构

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMResponse {
    private String content;
    private List<ToolCall> toolCalls;
    private String finishReason;

    public boolean hasContent() {
        return content != null && !content.isEmpty();
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolCall {
    private String id;
    private String name;
    private Map<String, Object> arguments;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LLMChunk {
    private String type; // content, tool_call, done
    private String content;
    private ToolCall toolCall;
}

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {
    private boolean success;
    private Object data;
    private String error;

    public static ToolResult success(Object data) {
        return ToolResult.builder().success(true).data(data).build();
    }

    public static ToolResult failure(String error) {
        return ToolResult.builder().success(false).error(error).build();
    }
}
```

### 4.3 DeepSeekClient 实现

```java
@Slf4j
public class DeepSeekClient implements LLMClient {

    private static final String API_URL = "https://api.deepseek.com/v1/chat/completions";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String apiKey;
    private final OkHttpClient httpClient;

    public DeepSeekClient(String apiKey) {
        this.apiKey = apiKey;
        this.httpClient = new OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build();
    }

    @Override
    public LLMResponse chat(String systemPrompt, List<Message> history, List<ToolDefinition> tools) {
        try {
            // 构建请求体
            Map<String, Object> requestBody = buildRequestBody(systemPrompt, history, tools, false);

            String jsonBody = OBJECT_MAPPER.writeValueAsString(requestBody);

            Request request = new Request.Builder()
                .url(API_URL)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    throw new LLMException("DeepSeek API error: " + response.code());
                }

                String responseBody = response.body().string();
                return parseResponse(responseBody);
            }
        } catch (Exception e) {
            log.error("DeepSeek chat failed", e);
            throw new LLMException("DeepSeek chat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<LLMChunk> streamChat(String systemPrompt, List<Message> history,
                                      List<ToolDefinition> tools) {
        return Flux.create(emitter -> {
            try {
                Map<String, Object> requestBody = buildRequestBody(systemPrompt, history, tools, true);

                String jsonBody = OBJECT_MAPPER.writeValueAsString(requestBody);

                Request request = new Request.Builder()
                    .url(API_URL)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                    .build();

                try (Response response = httpClient.newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        emitter.error(new LLMException("DeepSeek API error: " + response.code()));
                        return;
                    }

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))) {

                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if ("[DONE]".equals(data)) {
                                    emitter.next(LLMChunk.builder().type("done").build());
                                    break;
                                }

                                LLMChunk chunk = parseChunk(data);
                                if (chunk != null) {
                                    emitter.next(chunk);
                                }
                            }
                        }
                    }
                }

                emitter.complete();
            } catch (Exception e) {
                log.error("DeepSeek stream failed", e);
                emitter.error(e);
            }
        });
    }

    private Map<String, Object> buildRequestBody(String systemPrompt, List<Message> history,
                                                   List<ToolDefinition> tools, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", "deepseek-chat");
        body.put("stream", stream);

        // 构建消息列表
        List<Map<String, Object>> messages = new ArrayList<>();

        // 添加 system prompt
        if (systemPrompt != null) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }

        // 添加历史消息
        for (Message msg : history) {
            messages.add(Map.of("role", msg.getRole(), "content", msg.getContent()));
        }

        body.put("messages", messages);

        // 添加工具定义
        if (tools != null && !tools.isEmpty()) {
            body.put("tools", buildTools(tools));
        }

        return body;
    }

    private List<Map<String, Object>> buildTools(List<ToolDefinition> tools) {
        return tools.stream()
            .map(tool -> Map.<String, Object>of(
                "type", "function",
                "function", Map.of(
                    "name", tool.getName(),
                    "description", tool.getDescription() != null ? tool.getDescription() : "",
                    "parameters", tool.getInputSchema()
                )
            ))
            .collect(Collectors.toList());
    }

    private LLMResponse parseResponse(String responseBody) throws JsonProcessingException {
        JsonNode root = OBJECT_MAPPER.readTree(responseBody);
        JsonNode choices = root.path("choices");

        if (choices.isArray() && choices.size() > 0) {
            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice.path("message");

            LLMResponse.LLMResponseBuilder builder = LLMResponse.builder();

            // 解析内容
            if (message.has("content")) {
                builder.content(message.get("content").asText());
            }

            // 解析工具调用
            if (message.has("tool_calls")) {
                List<ToolCall> toolCalls = new ArrayList<>();
                for (JsonNode tc : message.get("tool_calls")) {
                    toolCalls.add(ToolCall.builder()
                        .id(tc.path("id").asText())
                        .name(tc.path("function").path("name").asText())
                        .arguments(parseArguments(tc.path("function").path("arguments")))
                        .build());
                }
                builder.toolCalls(toolCalls);
            }

            builder.finishReason(firstChoice.path("finish_reason").asText());
            return builder.build();
        }

        return LLMResponse.builder().build();
    }

    private Map<String, Object> parseArguments(JsonNode argsNode) {
        try {
            return OBJECT_MAPPER.treeToValue(argsNode, Map.class);
        } catch (JsonProcessingException e) {
            return Collections.emptyMap();
        }
    }

    private LLMChunk parseChunk(String data) {
        try {
            JsonNode root = OBJECT_MAPPER.readTree(data);
            JsonNode choices = root.path("choices");

            if (choices.isArray() && choices.size() > 0) {
                JsonNode delta = choices.get(0).path("delta");

                if (delta.has("content")) {
                    return LLMChunk.builder()
                        .type("content")
                        .content(delta.get("content").asText())
                        .build();
                }

                if (delta.has("tool_calls")) {
                    JsonNode tc = delta.get("tool_calls").get(0);
                    return LLMChunk.builder()
                        .type("tool_call")
                        .toolCall(ToolCall.builder()
                            .id(tc.path("id").asText())
                            .name(tc.path("function").path("name").asText())
                            .arguments(parseArguments(tc.path("function").path("arguments")))
                            .build())
                        .build();
                }
            }
        } catch (Exception e) {
            log.warn("Failed to parse chunk: {}", data, e);
        }
        return null;
    }
}
```

### 4.4 LLMClientFactory

```java
@Service
@RequiredArgsConstructor
public class LLMClientFactory {

    private final ConfigService configService;

    public LLMClient createClient(String provider) {
        String apiKey = configService.getApiKey(provider);

        return switch (provider.toLowerCase()) {
            case "deepseek" -> new DeepSeekClient(apiKey);
            case "openai" -> new OpenAIClient(apiKey);
            case "claude" -> new ClaudeClient(apiKey);
            default -> throw new IllegalArgumentException("Unknown LLM provider: " + provider);
        };
    }
}
```

---

## 5. REST 接口设计

### 5.1 ChatController

```java
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Slf4j
public class ChatController {

    private final ChatService chatService;
    private final ConversationService conversationService;

    /**
     * 流式对话 (SSE)
     */
    @PostMapping(value = "/chat", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<ChatEvent>> chat(
            @Valid @RequestBody ChatRequest request,
            @RequestHeader("X-Session-Id") String sessionId) {

        log.info("Chat request from session: {}", sessionId);

        return chatService.chatStream(sessionId, request);
    }

    /**
     * 获取对话列表
     */
    @GetMapping("/conversations")
    public ResponseEntity<ApiResponse<List<ConversationDTO>>> getConversations(
            @RequestHeader("X-Session-Id") String sessionId) {

        List<Conversation> conversations = conversationService.getUserConversations(sessionId);

        List<ConversationDTO> dtos = conversations.stream()
            .map(this::toDTO)
            .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(dtos));
    }

    /**
     * 获取对话详情
     */
    @GetMapping("/conversations/{conversationId}")
    public ResponseEntity<ApiResponse<ConversationDetailDTO>> getConversation(
            @PathVariable String conversationId,
            @RequestHeader("X-Session-Id") String sessionId) {

        Conversation conversation = conversationService.getConversation(conversationId, sessionId);
        List<Message> messages = conversationService.getRecentMessages(conversationId, 100);

        return ResponseEntity.ok(ApiResponse.success(
            ConversationDetailDTO.builder()
                .conversation(toDTO(conversation))
                .messages(messages.stream().map(this::toMessageDTO).collect(Collectors.toList()))
                .build()
        ));
    }

    /**
     * 删除对话
     */
    @DeleteMapping("/conversations/{conversationId}")
    public ResponseEntity<ApiResponse<Void>> deleteConversation(
            @PathVariable String conversationId,
            @RequestHeader("X-Session-Id") String sessionId) {

        conversationService.deleteConversation(conversationId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    // === 私有方法 ===

    private ConversationDTO toDTO(Conversation conv) {
        return ConversationDTO.builder()
            .conversationId(conv.getConversationId())
            .title(conv.getTitle())
            .messageCount(conv.getMessageCount())
            .createdAt(conv.getCreatedAt())
            .updatedAt(conv.getUpdatedAt())
            .build();
    }

    private MessageDTO toMessageDTO(Message msg) {
        return MessageDTO.builder()
            .messageId(msg.getMessageId())
            .role(msg.getRole())
            .content(msg.getContent())
            .toolName(msg.getToolName())
            .createdAt(msg.getCreatedAt())
            .build();
    }
}
```

### 5.2 请求/响应 DTO

```java
// 对话请求
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatRequest {
    @NotBlank(message = "内容不能为空")
    private String content;

    private String conversationId;
}

// 对话 DTO
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDTO {
    private String conversationId;
    private String title;
    private Integer messageCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}

// 消息 DTO
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageDTO {
    private String messageId;
    private String role;
    private String content;
    private String toolName;
    private LocalDateTime createdAt;
}

// 对话详情 DTO
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationDetailDTO {
    private ConversationDTO conversation;
    private List<MessageDTO> messages;
}
```

---

## 6. 数据库设计

### 6.1 t_conversation 表

```sql
CREATE TABLE t_conversation (
    id              BIGINT UNSIGNED     PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    conversation_id VARCHAR(64)         NOT NULL COMMENT '对话唯一标识',
    session_id      VARCHAR(64)         NOT NULL COMMENT '会话ID',
    title           VARCHAR(256)        COMMENT '对话标题',
    message_count   INT                 NOT NULL DEFAULT 0 COMMENT '消息数量',
    is_deleted      TINYINT             NOT NULL DEFAULT 0 COMMENT '是否已删除',
    created_at      TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,

    UNIQUE KEY uk_conversation_id (conversation_id),
    INDEX idx_session_id (session_id),
    INDEX idx_is_deleted (is_deleted)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='对话表';
```

### 6.2 t_message 表

```sql
CREATE TABLE t_message (
    id              BIGINT UNSIGNED     PRIMARY KEY AUTO_INCREMENT COMMENT '主键ID',
    message_id      VARCHAR(64)         NOT NULL COMMENT '消息唯一标识',
    conversation_id VARCHAR(64)         NOT NULL COMMENT '对话ID',
    role            VARCHAR(32)         NOT NULL COMMENT '角色: user/assistant/tool',
    content         TEXT                COMMENT '消息内容',
    tool_calls      TEXT                COMMENT '工具调用(JSON)',
    tool_call_id    VARCHAR(64)         COMMENT '工具调用ID',
    tool_name       VARCHAR(128)        COMMENT '工具名称',
    created_at      TIMESTAMP           NOT NULL DEFAULT CURRENT_TIMESTAMP,

    UNIQUE KEY uk_message_id (message_id),
    INDEX idx_conversation_id (conversation_id),
    INDEX idx_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息表';
```

---

## 7. 接口测试用例

### 7.1 流式对话

```http
POST /api/v1/chat
Content-Type: application/json
X-Session-Id: abc123def456
Accept: text/event-stream

{
  "content": "帮我上传一个 Nginx 软件"
}

# 期望响应 (SSE 流)
event: content
data: {"content":"好的，我来帮您上传 Nginx 软件。"}

event: tool_use
data: {"tool":"web_search","args":{"query":"Nginx 官网下载"}}

event: tool_result
data: {"success":true,"data":{...}}

event: content
data: {"content":"我找到了 Nginx 的官方信息..."}

event: done
data: {}
```

### 7.2 获取对话列表

```http
GET /api/v1/conversations
X-Session-Id: abc123def456

# 期望响应
{
  "code": 200,
  "message": "success",
  "data": [
    {
      "conversationId": "conv001",
      "title": "上传 Nginx 软件",
      "messageCount": 5,
      "createdAt": "2026-03-25T10:00:00",
      "updatedAt": "2026-03-25T10:05:00"
    }
  ]
}
```

---

## 8. 错误码定义

| 错误码 | HTTP 状态 | 说明 |
|--------|----------|------|
| SESSION_INVALID | 401 | 会话无效或已过期 |
| CHAT_ERROR | 500 | 对话处理错误 |
| MAX_ROUNDS | 500 | 达到最大工具调用轮次 |
| LLM_ERROR | 503 | LLM API 调用失败 |
