package com.ai.assistant.infrastructure.mcp;

import com.ai.assistant.infrastructure.mcp.dto.MCPRequest;
import com.ai.assistant.infrastructure.mcp.dto.MCPResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * MCP HTTP 客户端
 */
@Slf4j
public class MCPClient {

    private static final String JSON_RPC_VERSION = "2.0";
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private final String gatewayUrl;
    private final OkHttpClient httpClient;
    private String authToken;
    private boolean useBasicAuth = false;

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
     * 设置 Basic Auth 凭据
     */
    public void setBasicAuth(String username, String password) {
        this.authToken = java.util.Base64.getEncoder().encodeToString(
            (username + ":" + password).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        this.useBasicAuth = true;
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
                "arguments", arguments != null ? arguments : Map.of()
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
                .post(RequestBody.create(requestBody, MediaType.parse("application/json")))
                .addHeader("Accept", "application/json, text/event-stream");

            // 添加认证头
            if (authToken != null && !authToken.isEmpty()) {
                String authHeader = useBasicAuth ? "Basic " + authToken : "Bearer " + authToken;
                httpRequestBuilder.addHeader("Authorization", authHeader);
            }

            try (Response response = httpClient.newCall(httpRequestBuilder.build()).execute()) {
                String responseBody = response.body() != null ? response.body().string() : "{}";

                log.debug("MCP request to {} - Status: {}, Body: {}", gatewayUrl, response.code(), responseBody.substring(0, Math.min(200, responseBody.length())));

                if (!response.isSuccessful()) {
                    log.warn("MCP request failed to {} - Status: {}, Body: {}", gatewayUrl, response.code(), responseBody);
                    return MCPResponse.builder()
                        .error(OBJECT_MAPPER.readTree("{\"code\": " + response.code() + ", \"message\": \"HTTP error: " + response.message() + "\"}"))
                        .build();
                }

                // Handle SSE response format
                String jsonData = extractJsonFromSse(responseBody);
                return OBJECT_MAPPER.readValue(jsonData, MCPResponse.class);
            }
        } catch (Exception e) {
            log.error("MCP request failed", e);
            try {
                return MCPResponse.builder()
                    .error(OBJECT_MAPPER.readTree("{\"code\": -1, \"message\": \"" + e.getMessage().replace("\"", "'") + "\"}"))
                    .build();
            } catch (IOException ex) {
                return MCPResponse.builder().build();
            }
        }
    }

    /**
     * 从 SSE 响应中提取 JSON 数据
     */
    private String extractJsonFromSse(String responseBody) {
        // If response starts with "event:" or "data:", it's SSE format
        if (responseBody.startsWith("event:") || responseBody.startsWith("data:")) {
            String[] lines = responseBody.split("\n");
            StringBuilder jsonData = new StringBuilder();
            for (String line : lines) {
                if (line.startsWith("data:")) {
                    String data = line.substring(5).trim();
                    if (!data.isEmpty()) {
                        jsonData.append(data);
                    }
                }
            }
            return jsonData.length() > 0 ? jsonData.toString() : responseBody;
        }
        return responseBody;
    }

    private String generateId() {
        return UUID.randomUUID().toString();
    }
}
