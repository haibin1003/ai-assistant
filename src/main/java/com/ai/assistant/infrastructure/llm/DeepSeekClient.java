package com.ai.assistant.infrastructure.llm;

import com.ai.assistant.application.config.ConfigService;
import com.ai.assistant.infrastructure.llm.dto.LLMResponse;
import com.ai.assistant.infrastructure.llm.dto.ToolCall;
import com.ai.assistant.infrastructure.mcp.dto.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Flux;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;

/**
 * DeepSeek LLM 客户端
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeepSeekClient implements LLMClient {

    @Value("${ai-assistant.llm.deepseek.api-url:https://api.deepseek.com/v1/chat/completions}")
    private String apiUrl;

    @Value("${ai-assistant.llm.deepseek.model:deepseek-chat}")
    private String model;

    @Value("${ai-assistant.llm.deepseek.timeout-ms:120000}")
    private int timeoutMs;

    private final ConfigService configService;
    private final ObjectMapper objectMapper;

    private OkHttpClient httpClient;

    private OkHttpClient getHttpClient() {
        if (httpClient == null) {
            httpClient = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(timeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .build();
        }
        return httpClient;
    }

    @Override
    public LLMResponse chat(String systemPrompt, List<com.ai.assistant.domain.entity.Message> history,
                             List<ToolDefinition> tools) {
        String apiKey = configService.getApiKey("deepseek");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("DeepSeek API key not configured");
        }

        try {
            Map<String, Object> requestBody = buildRequestBody(systemPrompt, history, tools, false);
            String jsonBody = objectMapper.writeValueAsString(requestBody);

            Request request = new Request.Builder()
                .url(apiUrl)
                .addHeader("Authorization", "Bearer " + apiKey)
                .addHeader("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                .build();

            try (Response response = getHttpClient().newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    String errorBody = response.body() != null ? response.body().string() : "Unknown error";
                    throw new RuntimeException("DeepSeek API error: " + response.code() + " - " + errorBody);
                }

                String responseBody = response.body().string();
                return parseResponse(responseBody);
            }
        } catch (Exception e) {
            log.error("DeepSeek chat failed", e);
            throw new RuntimeException("DeepSeek chat failed: " + e.getMessage(), e);
        }
    }

    @Override
    public Flux<String> streamChat(String systemPrompt, List<com.ai.assistant.domain.entity.Message> history,
                                    List<ToolDefinition> tools) {
        String apiKey = configService.getApiKey("deepseek");
        if (apiKey == null || apiKey.isEmpty()) {
            return Flux.error(new IllegalStateException("DeepSeek API key not configured"));
        }

        return Flux.create(emitter -> {
            try {
                Map<String, Object> requestBody = buildRequestBody(systemPrompt, history, tools, true);
                String jsonBody = objectMapper.writeValueAsString(requestBody);

                Request request = new Request.Builder()
                    .url(apiUrl)
                    .addHeader("Authorization", "Bearer " + apiKey)
                    .addHeader("Content-Type", "application/json")
                    .post(RequestBody.create(jsonBody, MediaType.parse("application/json")))
                    .build();

                try (Response response = getHttpClient().newCall(request).execute()) {
                    if (!response.isSuccessful()) {
                        emitter.error(new RuntimeException("DeepSeek API error: " + response.code()));
                        return;
                    }

                    try (BufferedReader reader = new BufferedReader(
                            new InputStreamReader(response.body().byteStream(), StandardCharsets.UTF_8))) {

                        String line;
                        while ((line = reader.readLine()) != null) {
                            if (line.startsWith("data: ")) {
                                String data = line.substring(6);
                                if ("[DONE]".equals(data)) {
                                    emitter.complete();
                                    break;
                                }
                                emitter.next(data);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                log.error("DeepSeek stream failed", e);
                emitter.error(e);
            }
        });
    }

    private Map<String, Object> buildRequestBody(String systemPrompt,
                                                   List<com.ai.assistant.domain.entity.Message> history,
                                                   List<ToolDefinition> tools, boolean stream) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("model", model);
        body.put("stream", stream);

        List<Map<String, Object>> messages = new ArrayList<>();

        if (systemPrompt != null && !systemPrompt.isEmpty()) {
            messages.add(Map.of("role", "system", "content", systemPrompt));
        }

        if (history != null) {
            for (com.ai.assistant.domain.entity.Message msg : history) {
                Map<String, Object> messageMap = new LinkedHashMap<>();
                messageMap.put("role", msg.getRole());

                if ("tool".equals(msg.getRole())) {
                    // Tool result message
                    messageMap.put("tool_call_id", msg.getToolCallId());
                    messageMap.put("content", msg.getContent() != null ? msg.getContent() : "");
                } else if ("assistant".equals(msg.getRole()) && msg.getToolCalls() != null && !msg.getToolCalls().isEmpty()) {
                    // Assistant message with tool calls
                    if (msg.getContent() != null && !msg.getContent().isEmpty()) {
                        messageMap.put("content", msg.getContent());
                    }
                    try {
                        @SuppressWarnings("unchecked")
                        List<Map<String, Object>> toolCalls = objectMapper.readValue(msg.getToolCalls(), List.class);
                        messageMap.put("tool_calls", toolCalls);
                    } catch (Exception e) {
                        log.warn("Failed to parse tool calls: {}", e.getMessage());
                        if (msg.getContent() != null) {
                            messageMap.put("content", msg.getContent());
                        }
                    }
                } else {
                    // Regular user or assistant message
                    messageMap.put("content", msg.getContent() != null ? msg.getContent() : "");
                }

                messages.add(messageMap);
            }
        }

        body.put("messages", messages);

        if (tools != null && !tools.isEmpty()) {
            body.put("tools", buildTools(tools));
        }

        return body;
    }

    private List<Map<String, Object>> buildTools(List<ToolDefinition> tools) {
        List<Map<String, Object>> result = new ArrayList<>();

        for (ToolDefinition tool : tools) {
            Map<String, Object> func = new LinkedHashMap<>();
            func.put("name", tool.getName());
            func.put("description", tool.getDescription() != null ? tool.getDescription() : "");

            try {
                if (tool.getInputSchema() != null && !tool.getInputSchema().isNull()) {
                    String schemaJson = objectMapper.writeValueAsString(tool.getInputSchema());
                    Map<String, Object> params = objectMapper.readValue(schemaJson, Map.class);
                    func.put("parameters", params);
                } else {
                    Map<String, Object> defaultSchema = new LinkedHashMap<>();
                    defaultSchema.put("type", "object");
                    defaultSchema.put("properties", new LinkedHashMap<>());
                    func.put("parameters", defaultSchema);
                }
            } catch (Exception e) {
                log.warn("Failed to parse tool schema for {}: {}", tool.getName(), e.getMessage());
                Map<String, Object> defaultSchema = new LinkedHashMap<>();
                defaultSchema.put("type", "object");
                defaultSchema.put("properties", new LinkedHashMap<>());
                func.put("parameters", defaultSchema);
            }

            Map<String, Object> toolDef = new LinkedHashMap<>();
            toolDef.put("type", "function");
            toolDef.put("function", func);
            result.add(toolDef);
        }

        return result;
    }

    private LLMResponse parseResponse(String responseBody) throws IOException {
        log.info("DeepSeek raw response: {}", responseBody);
        JsonNode root = objectMapper.readTree(responseBody);
        JsonNode choices = root.path("choices");

        if (choices.isArray() && choices.size() > 0) {
            JsonNode firstChoice = choices.get(0);
            JsonNode message = firstChoice.path("message");

            LLMResponse.LLMResponseBuilder builder = LLMResponse.builder();

            if (message.has("content") && !message.get("content").isNull()) {
                builder.content(message.get("content").asText());
            }

            if (message.has("tool_calls") && message.get("tool_calls").isArray()) {
                List<ToolCall> toolCalls = new ArrayList<>();
                for (JsonNode tc : message.get("tool_calls")) {
                    String id = tc.path("id").asText();
                    String name = tc.path("function").path("name").asText();
                    JsonNode argsNode = tc.path("function").path("arguments");
                    log.info("Tool call from DeepSeek: id={}, name={}, argsNode={}", id, name, argsNode);
                    Map<String, Object> args = parseArguments(argsNode);
                    log.info("Parsed arguments: {}", args);
                    toolCalls.add(ToolCall.builder()
                        .id(id)
                        .name(name)
                        .arguments(args)
                        .build());
                }
                builder.toolCalls(toolCalls);
            }

            builder.finishReason(firstChoice.path("finish_reason").asText(null));
            return builder.build();
        }

        return LLMResponse.builder().build();
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> parseArguments(JsonNode argsNode) {
        try {
            // 如果是文本节点（JSON 字符串），先解析为对象
            if (argsNode.isTextual()) {
                String argsStr = argsNode.asText();
                return objectMapper.readValue(argsStr, Map.class);
            }
            // 如果是对象节点，直接转换
            return objectMapper.treeToValue(argsNode, Map.class);
        } catch (Exception e) {
            log.warn("Failed to parse tool arguments: {}", argsNode, e);
            return Collections.emptyMap();
        }
    }
}
