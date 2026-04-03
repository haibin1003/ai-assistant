package com.ai.assistant.application.chat;

import com.ai.assistant.application.chat.dto.ChatEvent;
import com.ai.assistant.application.chat.dto.ChatRequest;
import com.ai.assistant.application.config.ConfigService;
import com.ai.assistant.application.context.ContextService;
import com.ai.assistant.application.context.dto.UserContextDTO;
import com.ai.assistant.application.skill.SkillLoaderService;
import com.ai.assistant.application.skill.dto.SkillMetadata;
import com.ai.assistant.application.system.SystemService;
import com.ai.assistant.common.exception.BizException;
import com.ai.assistant.domain.entity.Conversation;
import com.ai.assistant.domain.entity.Message;
import com.ai.assistant.infrastructure.llm.LLMClient;
import com.ai.assistant.infrastructure.llm.LLMClientFactory;
import com.ai.assistant.infrastructure.llm.dto.LLMResponse;
import com.ai.assistant.infrastructure.llm.dto.ToolCall;
import com.ai.assistant.infrastructure.mcp.dto.ToolDefinition;
import com.ai.assistant.infrastructure.mcp.ToolRouter;
import com.ai.assistant.infrastructure.mcp.dto.ToolResult;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 对话服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ChatService {

    private final ConversationService conversationService;
    private final SystemPromptBuilder systemPromptBuilder;
    private final ToolRouter toolRouter;
    private final LLMClientFactory llmClientFactory;
    private final ConfigService configService;
    private final ContextService contextService;
    private final SystemService systemService;
    private final ObjectMapper objectMapper;
    private SkillLoaderService skillLoaderService;

    @Value("${ai-assistant.chat.max-tool-rounds:2}")
    private int maxToolRounds;

    @Value("${ai-assistant.chat.max-history-messages:20}")
    private int maxHistoryMessages;

    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setSkillLoaderService(SkillLoaderService skillLoaderService) {
        this.skillLoaderService = skillLoaderService;
    }

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
        // 1. 获取用户上下文（支持游客模式）
        UserContextDTO context = contextService.getContext(sessionId);

        // 如果没有上下文，创建游客上下文
        if (context == null) {
            // 游客模式：只能使用公开工具
            context = UserContextDTO.builder()
                .sessionId(sessionId)
                .username("guest")
                .userId("guest")
                .roles(List.of("GUEST"))
                .build();
            log.info("Guest mode for session: {}", sessionId);
        }

        // 2. 获取或创建对话
        Conversation conversation = conversationService.getOrCreateConversation(
            sessionId, request.getConversationId());

        // 3. 保存用户消息
        conversationService.addMessage(conversation.getConversationId(),
            "user", request.getContent());

        // 4. 构建 System Prompt
        String systemPrompt = systemPromptBuilder.buildSystemPrompt(context);

        // 5. 获取历史消息
        List<Message> history = conversationService.getRecentMessages(
            conversation.getConversationId(), maxHistoryMessages);

        // 6. 获取可用工具
        List<ToolDefinition> tools = getAvailableTools(context);

        // 7. 获取 LLM 客户端
        String provider = configService.getLLMProvider();
        LLMClient llmClient = llmClientFactory.createClient(provider);

        // 8. 开始对话循环
        processDialogLoop(llmClient, systemPrompt, history, request.getContent(),
            tools, context, conversation.getConversationId(), emitter);

        // 9. 完成
        emitter.next(ServerSentEvent.builder(ChatEvent.done()).build());
        emitter.complete();
    }

    private void processDialogLoop(LLMClient llmClient, String systemPrompt,
                                     List<Message> history, String userContent,
                                     List<ToolDefinition> tools, UserContextDTO context,
                                     String conversationId,
                                     FluxSink<ServerSentEvent<ChatEvent>> emitter) {

        List<Message> dialogHistory = new ArrayList<>(history);
        dialogHistory.add(createUserMessage(userContent));

        int toolRounds = 0;

        // 发送用户消息已收到的确认
        emitter.next(ServerSentEvent.builder(ChatEvent.thinking("我正在理解您的问题...")).build());

        // 获取已加载的 Skills 信息
        Map<String, SkillMetadata> loadedSkills = skillLoaderService != null ?
            skillLoaderService.getLoadedSkills() : Collections.emptyMap();

        // 如果有已加载的 Skills，提示用户
        if (!loadedSkills.isEmpty()) {
            String skillNames = loadedSkills.values().stream()
                .map(SkillMetadata::getName)
                .collect(Collectors.joining(", "));
            emitter.next(ServerSentEvent.builder(ChatEvent.thinking("已加载技能: " + skillNames)).build());
        }

        while (toolRounds < maxToolRounds) {
            // 调用 LLM 之前发送思考中状态
            if (toolRounds == 0) {
                emitter.next(ServerSentEvent.builder(ChatEvent.thinking("让我思考一下如何回答...")).build());
            } else {
                emitter.next(ServerSentEvent.builder(ChatEvent.thinking("处理工具返回结果，继续推理...")).build());
            }

            // 调用 LLM
            LLMResponse response = llmClient.chat(systemPrompt, dialogHistory, tools);

            // 处理响应
            boolean hasContent = response.hasContent() && response.getContent() != null && !response.getContent().isEmpty();
            boolean hasToolCalls = response.hasToolCalls();

            if (hasContent && !hasToolCalls) {
                // 只有 content，没有 tool calls → 直接发送并保存
                log.info("Sending content (no tool calls): {}", response.getContent());
                emitter.next(ServerSentEvent.builder(ChatEvent.content(response.getContent())).build());
                conversationService.addMessage(conversationId, "assistant", response.getContent());
                break;
            }

            if (hasToolCalls) {
                List<ToolCall> toolCalls = response.getToolCalls();

                // 发送推理过程
                String reasoning = String.format("我需要调用 %d 个工具来获取更多信息", toolCalls.size());
                emitter.next(ServerSentEvent.builder(ChatEvent.reasoning(reasoning)).build());

                // 将 assistant 消息（含 content + tool calls）加入历史
                dialogHistory.add(createAssistantMessageWithToolCalls(
                    hasContent ? response.getContent() : "", toolCalls));

                // 发送 content（仅当有 content 时）
                if (hasContent) {
                    emitter.next(ServerSentEvent.builder(ChatEvent.content(response.getContent())).build());
                    conversationService.addMessage(conversationId, "assistant", response.getContent());
                }

                for (ToolCall toolCall : toolCalls) {
                    // 发送工具调用开始
                    String toolThinking = String.format("正在调用工具: %s", toolCall.getName());
                    emitter.next(ServerSentEvent.builder(ChatEvent.thinking(toolThinking)).build());

                    emitter.next(ServerSentEvent.builder(
                        ChatEvent.toolUse(toolCall.getName(), toolCall.getArguments())).build());

                    ToolResult result = executeToolCall(toolCall, context);

                    // Send tool result - include error message if failed
                    Object resultData = result.isSuccess() ? result.getData() : result.getError();
                    emitter.next(ServerSentEvent.builder(
                        ChatEvent.toolResult(result.isSuccess(), resultData)).build());

                    // Add tool result message
                    dialogHistory.add(createToolMessage(toolCall.getId(), result));

                    // 保存消息
                    conversationService.addToolMessage(conversationId,
                        toolCall.getId(), toolCall.getName(),
                        result.isSuccess() ? result.getData().toString() : result.getError());
                }

                toolRounds++;
            } else {
                // 既没有 content 也没有 tool calls（异常响应）
                log.warn("LLM returned empty response");
                break;
            }
        }

        if (toolRounds >= maxToolRounds) {
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
        List<ToolDefinition> tools = new ArrayList<>();

        // 检查是否登录（游客模式只能使用内置工具）
        boolean isLoggedIn = context.getUserId() != null && !"guest".equals(context.getUserId());

        log.info("getAvailableTools: isLoggedIn={}, userId={}, systemId={}, authUsername={}, authPassword={}",
            isLoggedIn, context.getUserId(), context.getSystemId(), context.getAuthUsername(), context.getAuthPassword() != null ? "set" : "null");

        if (isLoggedIn && context.getSystemId() != null) {
            // 登录用户：获取系统的所有工具
            tools.addAll(systemService.getSystemTools(
                context.getSystemId(),
                context.getAuthUsername(),
                context.getAuthPassword()));
        }

        // 内置工具（web_search, browser_*）对所有用户开放
        tools.addAll(getBuiltInTools());

        // 添加已加载的 Skills 工具（对所有用户开放，包括游客）
        if (skillLoaderService != null) {
            List<ToolDefinition> skillTools = skillLoaderService.getSkillToolDefinitionsAsToolDefinitions();
            tools.addAll(skillTools);
            log.info("Adding {} skill tools", skillTools.size());
        }

        log.info("Total tools available: {}", tools.size());
        return tools;
    }

    private List<ToolDefinition> getBuiltInTools() {
        return List.of(
            // Web search tool
            ToolDefinition.builder()
                .name("web_search")
                .description("Search the web and return search results")
                .inputSchema(buildSchema(
                    Map.of(
                        "query", Map.of("type", "string", "description", "Search keyword")
                    ),
                    List.of("query")
                ))
                .build(),
            // Browser navigate tool
            ToolDefinition.builder()
                .name("browser_navigate")
                .description("Navigate to a specific URL")
                .inputSchema(buildSchema(
                    Map.of(
                        "url", Map.of("type", "string", "description", "URL to navigate to")
                    ),
                    List.of("url")
                ))
                .build(),
            // Browser snapshot tool
            ToolDefinition.builder()
                .name("browser_snapshot")
                .description("Get a snapshot of the current page content")
                .inputSchema(buildSchema(Map.of(), null))
                .build(),
            // Generate Excel tool
            ToolDefinition.builder()
                .name("generate_excel")
                .description("Generate Excel document from data. Parameters: title, headers (array), rows (array), sheet_name")
                .inputSchema(buildSchema(
                    Map.of(
                        "title", Map.of("type", "string", "description", "Document title"),
                        "headers", Map.of("type", "array", "description", "Column headers array, e.g., [\"Name\", \"Version\"]"),
                        "rows", Map.of("type", "array", "description", "Data rows array, e.g., [[\"Nginx\", \"1.25.4\"], ...]"),
                        "sheet_name", Map.of("type", "string", "description", "Sheet name, default Sheet1")
                    ),
                    List.of("title", "headers", "rows")
                ))
                .build(),
            // Generate Word tool
            ToolDefinition.builder()
                .name("generate_word")
                .description("Generate Word document from data. Parameters: title, headers (array), rows (array)")
                .inputSchema(buildSchema(
                    Map.of(
                        "title", Map.of("type", "string", "description", "Document title"),
                        "headers", Map.of("type", "array", "description", "Column headers array, e.g., [\"Name\", \"Version\"]"),
                        "rows", Map.of("type", "array", "description", "Data rows array, e.g., [[\"Nginx\", \"1.25.4\"], ...]")
                    ),
                    List.of("title", "headers", "rows")
                ))
                .build()
        );
    }

    private JsonNode buildSchema(Map<String, Object> properties, List<String> required) {
        var schema = objectMapper.createObjectNode();
        schema.put("type", "object");
        schema.set("properties", objectMapper.valueToTree(properties));
        if (required != null && !required.isEmpty()) {
            schema.set("required", objectMapper.valueToTree(required));
        }
        return schema;
    }

    private Message createUserMessage(String content) {
        return Message.builder()
            .role("user")
            .content(content)
            .build();
    }

    private Message createAssistantMessage(String content, ToolCall toolCall) {
        return Message.builder()
            .role("assistant")
            .content(content)
            .toolCalls(toolCall != null ? toolCall.toString() : null)
            .build();
    }

    private Message createAssistantMessageWithToolCalls(String content, List<ToolCall> toolCalls) {
        try {
            // Serialize tool calls to JSON format expected by DeepSeek
            List<Map<String, Object>> toolCallsJson = new ArrayList<>();
            for (ToolCall tc : toolCalls) {
                Map<String, Object> functionMap = new LinkedHashMap<>();
                functionMap.put("name", tc.getName());
                // Arguments must be a JSON string, not an object
                functionMap.put("arguments", tc.getArguments() != null ?
                    objectMapper.writeValueAsString(tc.getArguments()) : "{}");

                Map<String, Object> toolCallMap = new LinkedHashMap<>();
                toolCallMap.put("id", tc.getId());
                toolCallMap.put("type", "function");
                toolCallMap.put("function", functionMap);
                toolCallsJson.add(toolCallMap);
            }

            return Message.builder()
                .role("assistant")
                .content(content)
                .toolCalls(objectMapper.writeValueAsString(toolCallsJson))
                .build();
        } catch (Exception e) {
            log.warn("Failed to serialize tool calls", e);
            return Message.builder()
                .role("assistant")
                .content(content)
                .build();
        }
    }

    private Message createToolMessage(String toolCallId, ToolResult result) {
        return Message.builder()
            .role("tool")
            .toolCallId(toolCallId)
            .content(result.isSuccess() ? result.getData().toString() : result.getError())
            .build();
    }
}
