package com.ai.assistant.infrastructure.mcp;

import com.ai.assistant.application.context.dto.UserContextDTO;
import com.ai.assistant.common.exception.BizException;
import com.ai.assistant.domain.entity.RegisteredSystem;
import com.ai.assistant.domain.repository.RegisteredSystemRepository;
import com.ai.assistant.infrastructure.mcp.dto.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ToolRouterTest {

    @Mock
    private RegisteredSystemRepository systemRepository;

    @InjectMocks
    private ToolRouter toolRouter;

    private UserContextDTO testContext;
    private RegisteredSystem testSystem;

    @BeforeEach
    void setUp() {
        testContext = UserContextDTO.builder()
            .sessionId("test-session")
            .systemId("osrm")
            .userId("user-001")
            .accessToken("test-token")
            .expiresAt(LocalDateTime.now().plusHours(24))
            .build();

        testSystem = RegisteredSystem.builder()
            .systemId("osrm")
            .systemName("Test System")
            .mcpGatewayUrl("http://localhost:3000/api/mcp")
            .authType("bearer")
            .toolPrefix("osrm_")
            .isActive(true)
            .build();
    }

    @Test
    @DisplayName("执行内置工具 - 已注册执行器")
    void execute_builtInTool_withExecutor() {
        Map<String, Object> args = Map.of("query", "test");

        // 注册内置工具执行器 (web_ 前缀)
        toolRouter.registerExecutor("web_search", (arguments, context) ->
            ToolResult.success(Map.of("items", Collections.emptyList())));

        ToolResult result = toolRouter.execute("web_search", args, testContext);

        assertTrue(result.isSuccess());
    }

    @Test
    @DisplayName("执行内置工具 - 未注册执行器")
    void execute_builtInTool_noExecutor() {
        Map<String, Object> args = Map.of("query", "test");

        ToolResult result = toolRouter.execute("web_search", args, testContext);

        assertFalse(result.isSuccess());
        assertTrue(result.getError().contains("not found"));
    }

    @Test
    @DisplayName("执行系统工具 - 系统不存在")
    void execute_systemTool_systemNotFound() {
        when(systemRepository.findByToolPrefixAndIsActiveTrue("unknown_"))
            .thenReturn(Optional.empty());

        BizException exception = assertThrows(BizException.class, () -> {
            toolRouter.execute("unknown_tool", Collections.emptyMap(), testContext);
        });

        assertTrue(exception.getMessage().contains("not found"));
    }

    @Test
    @DisplayName("解析工具名 - 无效格式")
    void execute_invalidToolName() {
        BizException exception = assertThrows(BizException.class, () -> {
            toolRouter.execute("invalidtoolname", Collections.emptyMap(), testContext);
        });

        assertTrue(exception.getMessage().contains("Invalid tool name"));
    }

    @Test
    @DisplayName("注册浏览器工具执行器")
    void registerBrowserTool() {
        // browser_ 前缀也是内置工具
        toolRouter.registerExecutor("browser_navigate", (args, context) ->
            ToolResult.success("OK"));

        ToolResult result = toolRouter.execute("browser_navigate", Map.of("url", "http://example.com"), testContext);

        assertTrue(result.isSuccess());
        assertNotNull(result.getData());
    }
}
