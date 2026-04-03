package com.ai.assistant.infrastructure.mcp;

import com.ai.assistant.infrastructure.mcp.dto.MCPResponse;
import com.ai.assistant.infrastructure.mcp.dto.ToolDefinition;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 工具发现服务
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ToolDiscovery {

    private final ObjectMapper objectMapper;

    /**
     * 发现目标系统的工具列表
     */
    public List<ToolDefinition> discoverTools(String mcpGatewayUrl) {
        return discoverTools(mcpGatewayUrl, null, null);
    }

    /**
     * 发现目标系统的工具列表（带 Basic Auth）
     */
    public List<ToolDefinition> discoverTools(String mcpGatewayUrl, String username, String password) {
        log.info("ToolDiscovery.discoverTools: url={}, username={}, hasPassword={}", mcpGatewayUrl, username, password != null);
        MCPClient client = new MCPClient(mcpGatewayUrl);

        if (username != null && password != null) {
            client.setBasicAuth(username, password);
        }

        MCPResponse response = client.listTools();
        log.info("ToolDiscovery response: isSuccess={}, error={}", response.isSuccess(), response.getErrorMessage());

        if (!response.isSuccess()) {
            log.warn("Tool discovery failed: {}", response.getErrorMessage());
            return List.of();
        }

        List<ToolDefinition> tools = new ArrayList<>();
        JsonNode toolsNode = response.getResult().path("tools");

        if (toolsNode.isArray()) {
            for (JsonNode toolNode : toolsNode) {
                ToolDefinition tool = ToolDefinition.builder()
                    .name(toolNode.path("name").asText())
                    .description(toolNode.path("description").asText())
                    .inputSchema(toolNode.path("inputSchema"))
                    .build();
                tools.add(tool);
            }
        }

        return tools;
    }

    /**
     * 健康检查 - 使用 portal 端点（无需认证）
     */
    public boolean checkHealth(String mcpGatewayUrl) {
        try {
            // Use portal endpoint for health check (no auth required)
            String portalUrl = mcpGatewayUrl.replace("/api/mcp", "/portal/mcp");
            MCPClient client = new MCPClient(portalUrl);
            MCPResponse response = client.ping();
            return response.isSuccess();
        } catch (Exception e) {
            log.warn("Health check failed for {}: {}", mcpGatewayUrl, e.getMessage());
            return false;
        }
    }
}
