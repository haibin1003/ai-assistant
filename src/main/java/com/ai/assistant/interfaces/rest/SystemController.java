package com.ai.assistant.interfaces.rest;

import com.ai.assistant.application.system.SystemService;
import com.ai.assistant.application.system.dto.*;
import com.ai.assistant.common.response.ApiResponse;
import com.ai.assistant.infrastructure.mcp.dto.ToolDefinition;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 系统管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/systems")
@RequiredArgsConstructor
public class SystemController {

    private final SystemService systemService;

    /**
     * 获取所有已注册系统
     */
    @GetMapping
    public ResponseEntity<ApiResponse<List<RegisteredSystemDTO>>> getAllSystems() {
        List<RegisteredSystemDTO> systems = systemService.getAllSystems();
        return ResponseEntity.ok(ApiResponse.success(systems));
    }

    /**
     * 注册新系统
     */
    @PostMapping
    public ResponseEntity<ApiResponse<RegisteredSystemDTO>> registerSystem(
            @Valid @RequestBody RegisterSystemRequest request) {
        RegisteredSystemDTO system = systemService.registerSystem(request);
        return ResponseEntity.ok(ApiResponse.success(system));
    }

    /**
     * 获取系统详情
     */
    @GetMapping("/{systemId}")
    public ResponseEntity<ApiResponse<RegisteredSystemDTO>> getSystem(
            @PathVariable String systemId) {
        RegisteredSystemDTO system = systemService.getSystem(systemId);
        return ResponseEntity.ok(ApiResponse.success(system));
    }

    /**
     * 更新系统配置
     */
    @PutMapping("/{systemId}")
    public ResponseEntity<ApiResponse<RegisteredSystemDTO>> updateSystem(
            @PathVariable String systemId,
            @Valid @RequestBody UpdateSystemRequest request) {
        RegisteredSystemDTO system = systemService.updateSystem(systemId, request);
        return ResponseEntity.ok(ApiResponse.success(system));
    }

    /**
     * 注销系统
     */
    @DeleteMapping("/{systemId}")
    public ResponseEntity<ApiResponse<Void>> unregisterSystem(
            @PathVariable String systemId) {
        systemService.unregisterSystem(systemId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 刷新工具缓存
     */
    @PostMapping("/{systemId}/refresh-tools")
    public ResponseEntity<ApiResponse<List<ToolDefinition>>> refreshToolCache(
            @PathVariable String systemId,
            @RequestBody(required = false) RefreshToolsRequest request) {
        String username = request != null ? request.getAuthUsername() : null;
        String password = request != null ? request.getAuthPassword() : null;
        List<ToolDefinition> tools = systemService.refreshToolCache(systemId, username, password);
        return ResponseEntity.ok(ApiResponse.success(tools));
    }

    /**
     * 获取系统工具列表
     */
    @GetMapping("/{systemId}/tools")
    public ResponseEntity<ApiResponse<List<ToolDefinition>>> getSystemTools(
            @PathVariable String systemId) {
        List<ToolDefinition> tools = systemService.getSystemTools(systemId);
        return ResponseEntity.ok(ApiResponse.success(tools));
    }

    /**
     * 系统健康检查
     */
    @GetMapping("/{systemId}/health")
    public ResponseEntity<ApiResponse<SystemHealth>> checkHealth(
            @PathVariable String systemId) {
        SystemHealth health = systemService.checkHealth(systemId);
        return ResponseEntity.ok(ApiResponse.success(health));
    }
}
