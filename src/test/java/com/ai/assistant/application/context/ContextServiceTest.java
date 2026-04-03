package com.ai.assistant.application.context;

import com.ai.assistant.application.context.dto.PushContextRequest;
import com.ai.assistant.application.context.dto.UserContextDTO;
import com.ai.assistant.domain.entity.Session;
import com.ai.assistant.domain.repository.SessionRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * ContextService 单元测试
 */
@ExtendWith(MockitoExtension.class)
class ContextServiceTest {

    @Mock
    private SessionRepository sessionRepository;

    @Spy
    private ObjectMapper objectMapper = new ObjectMapper();

    @InjectMocks
    private ContextService contextService;

    private PushContextRequest testRequest;
    private Session testSession;

    @BeforeEach
    void setUp() {
        testRequest = PushContextRequest.builder()
            .sessionId("test-session-123")
            .systemId("osrm")
            .user(PushContextRequest.UserInfo.builder()
                .id("user-001")
                .username("testuser")
                .roles(List.of("USER"))
                .permissions(List.of("read", "write"))
                .build())
            .credentials(PushContextRequest.Credentials.builder()
                .accessToken("test-token")
                .expiresIn(7200L)
                .build())
            .build();

        testSession = Session.builder()
            .sessionId("test-session-123")
            .systemId("osrm")
            .userId("user-001")
            .username("testuser")
            .userContext("{\"roles\":[\"USER\"],\"permissions\":[\"read\",\"write\"]}")
            .accessToken("test-token")
            .expiresAt(LocalDateTime.now().plusHours(24))
            .isDeleted(false)
            .build();
    }

    @Test
    @DisplayName("推送用户上下文 - 新会话")
    void pushContext_newSession() {
        when(sessionRepository.findBySessionId(anyString())).thenReturn(Optional.empty());
        when(sessionRepository.save(any(Session.class))).thenReturn(testSession);

        contextService.pushContext(testRequest);

        verify(sessionRepository).save(any(Session.class));
    }

    @Test
    @DisplayName("推送用户上下文 - 更新现有会话")
    void pushContext_updateExistingSession() {
        when(sessionRepository.findBySessionId(anyString())).thenReturn(Optional.of(testSession));
        when(sessionRepository.save(any(Session.class))).thenReturn(testSession);

        contextService.pushContext(testRequest);

        verify(sessionRepository).save(any(Session.class));
    }

    @Test
    @DisplayName("获取用户上下文 - 从数据库获取")
    void getContext_fromDatabase() {
        when(sessionRepository.findBySessionIdAndIsDeletedFalse(anyString())).thenReturn(Optional.of(testSession));

        UserContextDTO result = contextService.getContext("test-session-123");

        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
    }

    @Test
    @DisplayName("获取用户上下文 - 缓存命中")
    void getContext_fromCache() {
        // 推送上下文（会更新缓存）
        when(sessionRepository.findBySessionId(anyString())).thenReturn(Optional.empty());
        when(sessionRepository.save(any(Session.class))).thenAnswer(invocation -> {
            Session session = invocation.getArgument(0);
            session.setExpiresAt(LocalDateTime.now().plusHours(24));
            return session;
        });
        contextService.pushContext(testRequest);

        // 再次获取，应该从缓存获取
        UserContextDTO result = contextService.getContext("test-session-123");

        assertNotNull(result);
        assertEquals("testuser", result.getUsername());
    }

    @Test
    @DisplayName("获取用户上下文 - 会话不存在")
    void getContext_notFound() {
        when(sessionRepository.findBySessionIdAndIsDeletedFalse(anyString())).thenReturn(Optional.empty());

        UserContextDTO result = contextService.getContext("non-existent-session");

        assertNull(result);
    }

    @Test
    @DisplayName("清除用户上下文")
    void clearContext() {
        when(sessionRepository.findBySessionId(anyString())).thenReturn(Optional.of(testSession));

        contextService.clearContext("test-session-123");

        verify(sessionRepository).save(any(Session.class));
    }

    @Test
    @DisplayName("验证会话有效性 - 有效")
    void isSessionValid_valid() {
        when(sessionRepository.findBySessionIdAndIsDeletedFalse(anyString())).thenReturn(Optional.of(testSession));

        boolean valid = contextService.isSessionValid("test-session-123");

        assertTrue(valid);
    }

    @Test
    @DisplayName("验证会话有效性 - 无效")
    void isSessionValid_invalid() {
        when(sessionRepository.findBySessionIdAndIsDeletedFalse(anyString())).thenReturn(Optional.empty());

        boolean valid = contextService.isSessionValid("non-existent-session");

        assertFalse(valid);
    }
}
