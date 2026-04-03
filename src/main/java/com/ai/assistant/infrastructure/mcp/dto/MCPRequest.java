package com.ai.assistant.infrastructure.mcp.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * MCP 请求
 */
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
