package com.ai.assistant.application.system;

import com.ai.assistant.application.system.dto.RegisterSystemRequest;
import com.ai.assistant.application.system.dto.RegisteredSystemDTO;
import com.ai.assistant.application.system.dto.UpdateSystemRequest;
import com.ai.assistant.common.exception.BizException;
import com.ai.assistant.domain.entity.RegisteredSystem;
import com.ai.assistant.domain.repository.RegisteredSystemRepository;
import com.ai.assistant.domain.repository.ToolCacheRepository;
import com.ai.assistant.infrastructure.mcp.ToolDiscovery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * SystemService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class SystemServiceTest {

    @Mock
    private RegisteredSystemRepository systemRepository;

    @Mock
    private ToolCacheRepository toolCacheRepository;

    @Mock
    private ToolDiscovery toolDiscovery;

    @InjectMocks
    private SystemService systemService;

    private RegisterSystemRequest registerRequest;
    private RegisteredSystem testSystem;

    @BeforeEach
    void setUp() {
        registerRequest = RegisterSystemRequest.builder()
            .systemId("osrm")
            .systemName("开源软件仓库管理")
            .mcpGatewayUrl("http://localhost:3000/api/mcp")
            .toolPrefix("osrm_")
            .authType("bearer")
            .build();

        testSystem = RegisteredSystem.builder()
            .id(1L)
            .systemId("osrm")
            .systemName("开源软件仓库管理")
            .mcpGatewayUrl("http://localhost:3000/api/mcp")
            .toolPrefix("osrm_")
            .authType("bearer")
            .isActive(true)
            .build();
    }

    @Test
    @DisplayName("注册新系统 - 成功")
    void registerSystem_success() {
        when(systemRepository.existsBySystemId("osrm")).thenReturn(false);
        when(systemRepository.save(any(RegisteredSystem.class))).thenReturn(testSystem);
        when(toolDiscovery.discoverTools(anyString())).thenReturn(List.of());

        RegisteredSystemDTO result = systemService.registerSystem(registerRequest);

        assertNotNull(result);
        assertEquals("osrm", result.getSystemId());
        assertEquals("开源软件仓库管理", result.getSystemName());
        verify(systemRepository).save(any(RegisteredSystem.class));
    }

    @Test
    @DisplayName("注册新系统 - 系统已存在")
    void registerSystem_alreadyExists() {
        when(systemRepository.existsBySystemId("osrm")).thenReturn(true);

        assertThrows(BizException.class, () -> {
            systemService.registerSystem(registerRequest);
        });
    }

    @Test
    @DisplayName("获取系统详情 - 成功")
    void getSystem_success() {
        when(systemRepository.findBySystemId("osrm")).thenReturn(Optional.of(testSystem));

        RegisteredSystemDTO result = systemService.getSystem("osrm");

        assertNotNull(result);
        assertEquals("osrm", result.getSystemId());
    }

    @Test
    @DisplayName("获取系统详情 - 系统不存在")
    void getSystem_notFound() {
        when(systemRepository.findBySystemId("unknown")).thenReturn(Optional.empty());

        assertThrows(BizException.class, () -> {
            systemService.getSystem("unknown");
        });
    }

    @Test
    @DisplayName("更新系统配置 - 成功")
    void updateSystem_success() {
        UpdateSystemRequest updateRequest = UpdateSystemRequest.builder()
            .systemName("更新后的名称")
            .build();

        when(systemRepository.findBySystemId("osrm")).thenReturn(Optional.of(testSystem));
        when(systemRepository.save(any(RegisteredSystem.class))).thenReturn(testSystem);

        RegisteredSystemDTO result = systemService.updateSystem("osrm", updateRequest);

        assertNotNull(result);
        verify(systemRepository).save(any(RegisteredSystem.class));
    }

    @Test
    @DisplayName("获取所有活跃系统")
    void getAllSystems() {
        when(systemRepository.findByIsActiveTrue()).thenReturn(List.of(testSystem));

        List<RegisteredSystemDTO> result = systemService.getAllSystems();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("osrm", result.get(0).getSystemId());
    }

    @Test
    @DisplayName("注销系统")
    void unregisterSystem() {
        when(systemRepository.findBySystemId("osrm")).thenReturn(Optional.of(testSystem));

        systemService.unregisterSystem("osrm");

        verify(systemRepository).save(any(RegisteredSystem.class));
        verify(toolCacheRepository).deleteBySystemId("osrm");
    }
}
