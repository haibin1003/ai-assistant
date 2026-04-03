package com.ai.assistant.infrastructure.mcp;

import com.ai.assistant.application.context.dto.UserContextDTO;
import com.ai.assistant.common.exception.BizException;
import com.ai.assistant.domain.entity.RegisteredSystem;
import com.ai.assistant.domain.repository.RegisteredSystemRepository;
import com.ai.assistant.infrastructure.mcp.dto.ToolDefinition;
import com.ai.assistant.infrastructure.mcp.dto.ToolResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 工具路由器
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ToolRouter {

    private final RegisteredSystemRepository systemRepository;
    private final Map<String, ToolExecutor> toolExecutors = new HashMap<>();

    private static final Set<String> BUILTIN_PREFIXES = Set.of("web_", "browser_", "generate_", "skill_");

    // Tools that should be routed to portal endpoint (public, no auth)
    private static final Set<String> PORTAL_TOOLS = Set.of(
        "search_software", "get_software_detail", "get_portal_stats",
        "get_popular_software", "list_software_types", "get_download_command"
    );

    // 默认 OSRM 凭据（开发环境使用）
    private static final String DEFAULT_OSRM_USERNAME = "admin";
    private static final String DEFAULT_OSRM_PASSWORD = "admin123";

    // Portal prefix for internal routing
    private static final String PORTAL_PREFIX = "portal_";

    /**
     * 执行工具调用
     */
    public ToolResult execute(String toolName, Map<String, Object> arguments, UserContextDTO context) {
        log.info("Executing tool: {} with args: {}", toolName, arguments);

        try {
            if (isBuiltInTool(toolName)) {
                return executeBuiltInTool(toolName, arguments, context);
            }

            // 检查是否是 portal 工具（无认证）
            if (PORTAL_TOOLS.contains(toolName)) {
                return executePortalTool(toolName, arguments);
            }

            ToolNameInfo info = parseToolName(toolName);

            RegisteredSystem system = systemRepository
                .findByToolPrefixAndIsActiveTrue(info.prefix())
                .orElseThrow(() -> new BizException("SYSTEM_NOT_FOUND",
                    "System not found for prefix: " + info.prefix()));

            MCPClient client = createMCPClient(system, context, info.actualName());

            var response = client.callTool(info.actualName(), arguments);

            if (response.isSuccess()) {
                return ToolResult.success(response.getResult());
            } else {
                // Use the improved error message from MCPResponse
                String errorMsg = response.getErrorMessage();
                return ToolResult.failure(errorMsg);
            }

        } catch (BizException e) {
            throw e;
        } catch (Exception e) {
            log.error("Tool execution failed: {}", toolName, e);
            return ToolResult.failure("Tool execution failed: " + e.getMessage());
        }
    }

    /**
     * 执行 portal 工具（无需认证）
     */
    private ToolResult executePortalTool(String toolName, Map<String, Object> arguments) {
        // 获取 OSRM 系统以获取 gateway URL
        RegisteredSystem osrmSystem = systemRepository.findByToolPrefixAndIsActiveTrue("osrm_").orElse(null);
        if (osrmSystem == null) {
            return ToolResult.failure("OSRM system not found");
        }

        String portalUrl = osrmSystem.getMcpGatewayUrl().replace("/api/mcp", "/portal/mcp");
        MCPClient client = new MCPClient(portalUrl);

        var response = client.callTool(toolName, arguments);
        if (response.isSuccess()) {
            return ToolResult.success(response.getResult());
        } else {
            return ToolResult.failure(response.getErrorMessage());
        }
    }

    /**
     * 注册工具执行器
     */
    public void registerExecutor(String toolName, ToolExecutor executor) {
        toolExecutors.put(toolName, executor);
    }

    private boolean isBuiltInTool(String toolName) {
        return BUILTIN_PREFIXES.stream().anyMatch(toolName::startsWith);
    }

    private ToolResult executeBuiltInTool(String toolName, Map<String, Object> arguments,
                                           UserContextDTO context) {
        ToolExecutor executor = toolExecutors.get(toolName);
        if (executor == null) {
            return ToolResult.failure("Built-in tool not found: " + toolName);
        }
        // Pass toolName via arguments so executor knows the original tool name
        arguments.put("_toolName", toolName);
        return executor.execute(arguments, context);
    }

    private ToolNameInfo parseToolName(String toolName) {
        int underscoreIndex = toolName.indexOf('_');
        if (underscoreIndex <= 0) {
            throw new BizException("INVALID_TOOL_NAME", "Invalid tool name format: " + toolName);
        }

        String prefix = toolName.substring(0, underscoreIndex + 1);
        String actualName = toolName.substring(underscoreIndex + 1);

        return new ToolNameInfo(prefix, actualName);
    }

    private MCPClient createMCPClient(RegisteredSystem system, UserContextDTO context, String actualToolName) {
        String gatewayUrl = system.getMcpGatewayUrl();

        // For portal tools, use portal endpoint (public, no auth required)
        if (PORTAL_TOOLS.contains(actualToolName)) {
            gatewayUrl = gatewayUrl.replace("/api/mcp", "/portal/mcp");
            log.info("Routing to portal endpoint: {}", gatewayUrl);
            return new MCPClient(gatewayUrl);
        }

        // For API tools, use API endpoint with authentication
        MCPClient client = new MCPClient(gatewayUrl);

        if (context != null) {
            if ("basic".equals(system.getAuthType())) {
                // 优先使用 session 中的凭据，否则使用默认凭据
                if (context.getAuthUsername() != null && context.getAuthPassword() != null) {
                    client.setBasicAuth(context.getAuthUsername(), context.getAuthPassword());
                    log.debug("Using session credentials for {}", system.getSystemId());
                } else {
                    client.setBasicAuth(DEFAULT_OSRM_USERNAME, DEFAULT_OSRM_PASSWORD);
                    log.debug("Using default credentials for {}", system.getSystemId());
                }
            } else if ("bearer".equals(system.getAuthType())) {
                if (context.getAccessToken() != null) {
                    client.setAuthToken(context.getAccessToken());
                }
            }
        } else {
            // 无 context 时使用默认凭据
            if ("basic".equals(system.getAuthType())) {
                client.setBasicAuth(DEFAULT_OSRM_USERNAME, DEFAULT_OSRM_PASSWORD);
                log.debug("Using default credentials (no context) for {}", system.getSystemId());
            }
        }

        return client;
    }

    private record ToolNameInfo(String prefix, String actualName) {}

    /**
     * 工具执行器接口
     */
    @FunctionalInterface
    public interface ToolExecutor {
        ToolResult execute(Map<String, Object> arguments, UserContextDTO context);
    }
}
