package com.ai.assistant.infrastructure.mcp.dto;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 工具执行结果
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ToolResult {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    private boolean success;
    private JsonNode data;
    private String error;

    public static ToolResult success(JsonNode data) {
        return ToolResult.builder().success(true).data(data).build();
    }

    public static ToolResult success(Object data) {
        return ToolResult.builder()
            .success(true)
            .data(OBJECT_MAPPER.valueToTree(data))
            .build();
    }

    public static ToolResult failure(String error) {
        return ToolResult.builder().success(false).error(error).build();
    }
}
