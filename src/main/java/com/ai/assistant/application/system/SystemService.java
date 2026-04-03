package com.ai.assistant.application.system;

import com.ai.assistant.application.system.dto.RegisterSystemRequest;
import com.ai.assistant.application.system.dto.RegisteredSystemDTO;
import com.ai.assistant.application.system.dto.SystemHealth;
import com.ai.assistant.application.system.dto.UpdateSystemRequest;
import com.ai.assistant.common.exception.BizException;
import com.ai.assistant.domain.entity.RegisteredSystem;
import com.ai.assistant.domain.entity.ToolCache;
import com.ai.assistant.domain.repository.RegisteredSystemRepository;
import com.ai.assistant.domain.repository.ToolCacheRepository;
import com.ai.assistant.infrastructure.mcp.ToolDiscovery;
import com.ai.assistant.infrastructure.mcp.dto.ToolDefinition;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 系统管理服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SystemService {

    private final RegisteredSystemRepository systemRepository;
    private final ToolCacheRepository toolCacheRepository;
    private final ToolDiscovery toolDiscovery;
    private final ObjectMapper objectMapper;

    /**
     * 注册新系统
     */
    @Transactional
    public RegisteredSystemDTO registerSystem(RegisterSystemRequest request) {
        // Check if system already exists
        Optional<RegisteredSystem> existingSystem = systemRepository.findBySystemId(request.getSystemId());

        RegisteredSystem system;
        if (existingSystem.isPresent()) {
            // Re-activate existing system
            system = existingSystem.get();
            system.setSystemName(request.getSystemName());
            system.setIconUrl(request.getIconUrl());
            system.setMcpGatewayUrl(request.getMcpGatewayUrl());
            system.setAuthType(request.getAuthType() != null ? request.getAuthType() : "none");
            system.setToolPrefix(request.getToolPrefix());
            system.setDescription(request.getDescription());
            system.setIsActive(true);
            log.info("Re-activating existing system: {}", request.getSystemId());
        } else {
            // Create new system
            system = RegisteredSystem.builder()
                .systemId(request.getSystemId())
                .systemName(request.getSystemName())
                .iconUrl(request.getIconUrl())
                .mcpGatewayUrl(request.getMcpGatewayUrl())
                .authType(request.getAuthType() != null ? request.getAuthType() : "none")
                .toolPrefix(request.getToolPrefix())
                .description(request.getDescription())
                .isActive(true)
                .build();
        }

        systemRepository.save(system);

        discoverAndCacheTools(system);

        log.info("System registered: {}", system.getSystemId());
        return toDTO(system);
    }

    /**
     * 更新系统配置
     */
    @Transactional
    public RegisteredSystemDTO updateSystem(String systemId, UpdateSystemRequest request) {
        RegisteredSystem system = systemRepository.findBySystemId(systemId)
            .orElseThrow(() -> new BizException("SYSTEM_NOT_FOUND",
                "System not found: " + systemId));

        if (request.getSystemName() != null) {
            system.setSystemName(request.getSystemName());
        }
        if (request.getIconUrl() != null) {
            system.setIconUrl(request.getIconUrl());
        }
        if (request.getMcpGatewayUrl() != null) {
            system.setMcpGatewayUrl(request.getMcpGatewayUrl());
            discoverAndCacheTools(system);
        }
        if (request.getAuthType() != null) {
            system.setAuthType(request.getAuthType());
        }
        if (request.getDescription() != null) {
            system.setDescription(request.getDescription());
        }

        systemRepository.save(system);
        log.info("System updated: {}", systemId);
        return toDTO(system);
    }

    /**
     * 注销系统
     */
    @Transactional
    public void unregisterSystem(String systemId) {
        RegisteredSystem system = systemRepository.findBySystemId(systemId)
            .orElseThrow(() -> new BizException("SYSTEM_NOT_FOUND",
                "System not found: " + systemId));

        system.setIsActive(false);
        systemRepository.save(system);
        toolCacheRepository.deleteBySystemId(systemId);

        log.info("System unregistered: {}", systemId);
    }

    /**
     * 获取系统详情
     */
    public RegisteredSystemDTO getSystem(String systemId) {
        RegisteredSystem system = systemRepository.findBySystemId(systemId)
            .orElseThrow(() -> new BizException("SYSTEM_NOT_FOUND",
                "System not found: " + systemId));
        return toDTO(system);
    }

    /**
     * 获取所有活跃系统
     */
    public List<RegisteredSystemDTO> getAllSystems() {
        return systemRepository.findByIsActiveTrue().stream()
            .map(this::toDTO)
            .collect(Collectors.toList());
    }

    /**
     * 刷新工具缓存
     */
    @Transactional
    public List<ToolDefinition> refreshToolCache(String systemId, String authUsername, String authPassword) {
        RegisteredSystem system = systemRepository.findBySystemIdAndIsActiveTrue(systemId)
            .orElseThrow(() -> new BizException("SYSTEM_NOT_FOUND",
                "System not found: " + systemId));

        return discoverAndCacheTools(system, authUsername, authPassword);
    }

    /**
     * 获取系统工具列表
     */
    public List<ToolDefinition> getSystemTools(String systemId) {
        return getSystemTools(systemId, null, null);
    }

    /**
     * 获取系统工具列表（带认证）
     */
    @Transactional
    public List<ToolDefinition> getSystemTools(String systemId, String authUsername, String authPassword) {
        RegisteredSystem system = systemRepository.findBySystemIdAndIsActiveTrue(systemId)
            .orElseThrow(() -> new BizException("SYSTEM_NOT_FOUND",
                "System not found: " + systemId));

        final String prefix = system.getToolPrefix();

        // 如果提供了认证信息，直接从 MCP 网关获取工具，不使用缓存
        if (authUsername != null && authPassword != null) {
            log.info("Fetching tools directly from MCP with auth for system: {}, username: {}", systemId, authUsername);
            try {
                // 直接调用 MCP 网关获取工具，不保存到缓存
                List<ToolDefinition> tools = toolDiscovery.discoverTools(
                    system.getMcpGatewayUrl(), authUsername, authPassword);
                log.info("Directly fetched {} tools with auth", tools.size());
                return tools.stream()
                    .map(tool -> toToolDefinitionWithPrefix(tool, prefix))
                    .collect(Collectors.toList());
            } catch (Exception e) {
                log.error("Failed to fetch tools with auth", e);
                // 如果获取失败，返回空列表
                return List.of();
            }
        }

        // 无认证时使用缓存（游客模式）
        List<ToolCache> cachedTools = toolCacheRepository.findBySystemId(systemId);
        if (cachedTools.isEmpty()) {
            log.info("No cached tools, discovering for guest");
            discoverAndCacheTools(system, null, null);
            cachedTools = toolCacheRepository.findBySystemId(systemId);
        } else {
            log.info("Using cached tools for guest: {} tools", cachedTools.size());
        }

        return cachedTools.stream()
            .map(cache -> toToolDefinitionWithPrefix(cache, prefix))
            .collect(Collectors.toList());
    }

    private ToolDefinition toToolDefinitionWithPrefix(ToolCache cache, String prefix) {
        String prefixedName = prefix + cache.getToolName();
        return ToolDefinition.builder()
            .name(prefixedName)
            .description(cache.getDescription())
            .inputSchema(parseJson(cache.getInputSchema()))
            .build();
    }

    private ToolDefinition toToolDefinitionWithPrefix(ToolDefinition tool, String prefix) {
        String prefixedName = prefix + tool.getName();
        return ToolDefinition.builder()
            .name(prefixedName)
            .description(tool.getDescription())
            .inputSchema(tool.getInputSchema())
            .build();
    }

    /**
     * 健康检查
     */
    public SystemHealth checkHealth(String systemId) {
        RegisteredSystem system = systemRepository.findBySystemId(systemId)
            .orElseThrow(() -> new BizException("SYSTEM_NOT_FOUND",
                "System not found: " + systemId));

        try {
            boolean healthy = toolDiscovery.checkHealth(system.getMcpGatewayUrl());
            return SystemHealth.builder()
                .systemId(systemId)
                .status(healthy ? "UP" : "DOWN")
                .checkedAt(LocalDateTime.now())
                .build();
        } catch (Exception e) {
            return SystemHealth.builder()
                .systemId(systemId)
                .status("DOWN")
                .error(e.getMessage())
                .checkedAt(LocalDateTime.now())
                .build();
        }
    }

    // === 私有方法 ===

    private List<ToolDefinition> discoverAndCacheTools(RegisteredSystem system) {
        return discoverAndCacheTools(system, null, null);
    }

    private List<ToolDefinition> discoverAndCacheTools(RegisteredSystem system, String authUsername, String authPassword) {
        try {
            String gatewayUrl = system.getMcpGatewayUrl();

            List<ToolDefinition> tools;

            // If credentials provided, try API endpoint first
            if (authUsername != null && authPassword != null) {
                log.info("Discovering tools with credentials for system: {}, url: {}", system.getSystemId(), gatewayUrl);
                tools = toolDiscovery.discoverTools(gatewayUrl, authUsername, authPassword);
                log.info("Tool discovery with auth returned {} tools", tools.size());
            } else {
                // Without credentials, try portal endpoint
                String portalUrl = gatewayUrl.replace("/api/mcp", "/portal/mcp");
                log.info("Trying portal endpoint for tool discovery: {}", portalUrl);
                tools = toolDiscovery.discoverTools(portalUrl, null, null);
                if (tools.isEmpty()) {
                    // Fallback to API endpoint without auth
                    log.info("Portal endpoint returned no tools, trying API endpoint without auth");
                    tools = toolDiscovery.discoverTools(gatewayUrl, null, null);
                }
            }

            // Clear old cache and save new tools in a separate transaction
            if (!tools.isEmpty()) {
                toolCacheRepository.deleteBySystemId(system.getSystemId());
                final String systemId = system.getSystemId();
                List<ToolCache> cacheList = tools.stream()
                    .map(tool -> ToolCache.builder()
                        .systemId(systemId)
                        .toolName(tool.getName())
                        .description(tool.getDescription())
                        .inputSchema(toJson(tool.getInputSchema()))
                        .build())
                    .collect(Collectors.toList());
                toolCacheRepository.saveAll(cacheList);
                log.info("Discovered and cached {} tools for system: {}", tools.size(), system.getSystemId());
            }

            return tools;
        } catch (Exception e) {
            log.error("Failed to discover tools for system: {}", system.getSystemId(), e);
            return Collections.emptyList();
        }
    }

    private RegisteredSystemDTO toDTO(RegisteredSystem system) {
        long toolCount = toolCacheRepository.countBySystemId(system.getSystemId());
        return RegisteredSystemDTO.builder()
            .systemId(system.getSystemId())
            .systemName(system.getSystemName())
            .iconUrl(system.getIconUrl())
            .mcpGatewayUrl(system.getMcpGatewayUrl())
            .authType(system.getAuthType())
            .toolPrefix(system.getToolPrefix())
            .description(system.getDescription())
            .isActive(system.getIsActive())
            .toolCount((int) toolCount)
            .createdAt(system.getCreatedAt())
            .updatedAt(system.getUpdatedAt())
            .build();
    }

    private ToolDefinition toToolDefinition(ToolCache cache) {
        return ToolDefinition.builder()
            .name(cache.getToolName())
            .description(cache.getDescription())
            .inputSchema(parseJson(cache.getInputSchema()))
            .build();
    }

    private String toJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            return "{}";
        }
    }

    private com.fasterxml.jackson.databind.JsonNode parseJson(String json) {
        try {
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            return objectMapper.createObjectNode();
        }
    }
}
