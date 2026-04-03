package com.ai.assistant.application.context;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ai.assistant.application.context.dto.PushContextRequest;
import com.ai.assistant.application.context.dto.UserContextDTO;
import com.ai.assistant.common.exception.BizException;
import com.ai.assistant.domain.entity.Session;
import com.ai.assistant.domain.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 用户上下文服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ContextService {

    private final SessionRepository sessionRepository;
    private final ObjectMapper objectMapper;

    @Value("${ai-assistant.session.default-expire-hours:24}")
    private int defaultSessionHours;

    // 内存缓存
    private final Map<String, UserContextDTO> contextCache = new ConcurrentHashMap<>();

    /**
     * 推送用户上下文
     */
    @Transactional
    public void pushContext(PushContextRequest request) {
        String sessionId = request.getSessionId();

        // 计算过期时间
        LocalDateTime expiresAt = LocalDateTime.now().plusHours(defaultSessionHours);
        LocalDateTime tokenExpiresAt = null;
        if (request.getCredentials() != null && request.getCredentials().getExpiresIn() != null) {
            tokenExpiresAt = LocalDateTime.now().plusSeconds(request.getCredentials().getExpiresIn());
        }

        // 构建用户上下文 JSON
        String userContextJson = buildUserContextJson(request);

        // 查找或创建 Session
        Optional<Session> existingSession = sessionRepository.findBySessionId(sessionId);
        Session session;

        if (existingSession.isPresent()) {
            session = existingSession.get();
            updateSession(session, request, userContextJson, tokenExpiresAt, expiresAt);
        } else {
            session = createSession(request, userContextJson, tokenExpiresAt, expiresAt);
        }

        sessionRepository.save(session);

        // 更新缓存
        UserContextDTO dto = buildDTO(session, request);
        contextCache.put(sessionId, dto);

        log.info("Context pushed for session: {}, user: {}, system: {}",
                sessionId, request.getUser().getUsername(), request.getSystemId());
    }

    /**
     * 获取用户上下文
     */
    public UserContextDTO getContext(String sessionId) {
        // 先查缓存
        UserContextDTO cached = contextCache.get(sessionId);
        if (cached != null && cached.isValid()) {
            return cached;
        }

        // 缓存未命中，查数据库
        Optional<Session> sessionOpt = sessionRepository.findBySessionIdAndIsDeletedFalse(sessionId);
        if (sessionOpt.isEmpty()) {
            return null;
        }

        Session session = sessionOpt.get();
        if (session.isExpired()) {
            cleanupSession(session);
            return null;
        }

        // 重建 DTO 并缓存
        UserContextDTO dto = buildDTOFromSession(session);
        contextCache.put(sessionId, dto);
        return dto;
    }

    /**
     * 清除用户上下文
     */
    @Transactional
    public void clearContext(String sessionId) {
        sessionRepository.findBySessionId(sessionId).ifPresent(session -> {
            session.setIsDeleted(true);
            sessionRepository.save(session);
        });
        contextCache.remove(sessionId);
        log.info("Context cleared for session: {}", sessionId);
    }

    /**
     * 获取会话过期时间
     */
    public LocalDateTime getSessionExpiresAt(String sessionId) {
        UserContextDTO context = getContext(sessionId);
        return context != null ? context.getExpiresAt() : null;
    }

    /**
     * 检查会话是否有效
     */
    public boolean isSessionValid(String sessionId) {
        return getContext(sessionId) != null;
    }

    /**
     * 获取用户的访问令牌
     */
    public String getAccessToken(String sessionId) {
        UserContextDTO context = getContext(sessionId);
        return context != null ? context.getAccessToken() : null;
    }

    /**
     * 清理过期会话 (定时任务)
     */
    @Scheduled(fixedRate = 3600000) // 每小时执行一次
    @Transactional
    public void cleanupExpiredSessions() {
        log.info("Starting expired sessions cleanup...");

        List<Session> expiredSessions = sessionRepository.findByExpiresAtBeforeAndIsDeletedFalse(
            LocalDateTime.now()
        );

        for (Session session : expiredSessions) {
            cleanupSession(session);
        }

        log.info("Cleaned up {} expired sessions", expiredSessions.size());
    }

    // === 私有方法 ===

    private String buildUserContextJson(PushContextRequest request) {
        try {
            Map<String, Object> contextMap = new LinkedHashMap<>();
            contextMap.put("userId", request.getUser().getId());
            contextMap.put("username", request.getUser().getUsername());
            contextMap.put("roles", request.getUser().getRoles());
            contextMap.put("permissions", request.getUser().getPermissions());
            contextMap.put("realName", request.getUser().getRealName());
            contextMap.put("email", request.getUser().getEmail());
            return objectMapper.writeValueAsString(contextMap);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize user context", e);
            return "{}";
        }
    }

    private Session createSession(PushContextRequest request, String userContextJson,
                                   LocalDateTime tokenExpiresAt, LocalDateTime expiresAt) {
        return Session.builder()
            .sessionId(request.getSessionId())
            .systemId(request.getSystemId())
            .userId(request.getUser().getId())
            .username(request.getUser().getUsername())
            .userContext(userContextJson)
            .accessToken(request.getCredentials() != null ?
                request.getCredentials().getAccessToken() : null)
            .refreshToken(request.getCredentials() != null ?
                request.getCredentials().getRefreshToken() : null)
            .tokenExpiresAt(tokenExpiresAt)
            .authUsername(request.getCredentials() != null ?
                request.getCredentials().getUsername() : null)
            .authPassword(request.getCredentials() != null ?
                request.getCredentials().getPassword() : null)
            .expiresAt(expiresAt)
            .isDeleted(false)
            .build();
    }

    private void updateSession(Session session, PushContextRequest request,
                                String userContextJson, LocalDateTime tokenExpiresAt,
                                LocalDateTime expiresAt) {
        session.setSystemId(request.getSystemId());
        session.setUserId(request.getUser().getId());
        session.setUsername(request.getUser().getUsername());
        session.setUserContext(userContextJson);
        if (request.getCredentials() != null) {
            session.setAccessToken(request.getCredentials().getAccessToken());
            session.setRefreshToken(request.getCredentials().getRefreshToken());
            session.setTokenExpiresAt(tokenExpiresAt);
            session.setAuthUsername(request.getCredentials().getUsername());
            session.setAuthPassword(request.getCredentials().getPassword());
        }
        session.setExpiresAt(expiresAt);
        session.setIsDeleted(false);
    }

    private UserContextDTO buildDTO(Session session, PushContextRequest request) {
        return UserContextDTO.builder()
            .sessionId(session.getSessionId())
            .systemId(session.getSystemId())
            .userId(request.getUser().getId())
            .username(request.getUser().getUsername())
            .roles(request.getUser().getRoles())
            .permissions(request.getUser().getPermissions())
            .accessToken(request.getCredentials() != null ?
                request.getCredentials().getAccessToken() : null)
            .authUsername(request.getCredentials() != null ?
                request.getCredentials().getUsername() : null)
            .authPassword(request.getCredentials() != null ?
                request.getCredentials().getPassword() : null)
            .expiresAt(session.getExpiresAt())
            .build();
    }

    private UserContextDTO buildDTOFromSession(Session session) {
        List<String> roles = Collections.emptyList();
        List<String> permissions = Collections.emptyList();

        if (session.getUserContext() != null) {
            try {
                Map<String, Object> contextMap = objectMapper.readValue(
                    session.getUserContext(), new TypeReference<Map<String, Object>>() {}
                );
                @SuppressWarnings("unchecked")
                List<String> rolesList = (List<String>) contextMap.getOrDefault("roles", Collections.emptyList());
                @SuppressWarnings("unchecked")
                List<String> permsList = (List<String>) contextMap.getOrDefault("permissions", Collections.emptyList());
                roles = rolesList;
                permissions = permsList;
            } catch (JsonProcessingException e) {
                log.warn("Failed to parse user context JSON", e);
            }
        }

        return UserContextDTO.builder()
            .sessionId(session.getSessionId())
            .systemId(session.getSystemId())
            .userId(session.getUserId())
            .username(session.getUsername())
            .roles(roles)
            .permissions(permissions)
            .accessToken(session.getAccessToken())
            .authUsername(session.getAuthUsername())
            .authPassword(session.getAuthPassword())
            .expiresAt(session.getExpiresAt())
            .build();
    }

    private void cleanupSession(Session session) {
        session.setIsDeleted(true);
        sessionRepository.save(session);
        contextCache.remove(session.getSessionId());
    }
}
