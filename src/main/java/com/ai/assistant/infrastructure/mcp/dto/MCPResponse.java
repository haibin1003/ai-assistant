package com.ai.assistant.infrastructure.mcp.dto;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * MCP 响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MCPResponse {
    private String jsonrpc;
    private String id;
    private JsonNode result;
    private JsonNode error;

    public boolean isSuccess() {
        // 1. Check if there's a top-level error
        if (error != null) {
            return false;
        }
        // 2. Check if result contains an error in content (MCP success=false case)
        if (result != null && result.has("content")) {
            var content = result.get("content");
            if (content.isArray()) {
                for (var item : content) {
                    if (item.has("isError") && item.get("isError").asBoolean()) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public String getErrorMessage() {
        // 1. Check top-level error
        if (error != null && error.has("message")) {
            return error.get("message").asText();
        }
        // 2. Check result content error
        if (result != null && result.has("content")) {
            var content = result.get("content");
            if (content.isArray() && content.size() > 0) {
                var first = content.get(0);
                if (first.has("text")) {
                    String text = first.get("text").asText();
                    // Handle cases like "错误：API错误 [0]: " which is empty
                    if (text != null && !text.isEmpty() && !text.equals("错误：API错误 [0]: ")) {
                        return text;
                    }
                }
            }
        }
        // 3. If error message is empty or unhelpful, provide more context
        return "API服务暂时不可用（可能原因：后端服务异常或网络问题）";
    }
}
