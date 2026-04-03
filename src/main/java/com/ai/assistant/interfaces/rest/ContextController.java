package com.ai.assistant.interfaces.rest;

import com.ai.assistant.application.context.ContextService;
import com.ai.assistant.application.context.dto.PushContextRequest;
import com.ai.assistant.application.context.dto.PushContextResponse;
import com.ai.assistant.application.context.dto.SessionValidResponse;
import com.ai.assistant.application.context.dto.UserContextDTO;
import com.ai.assistant.common.exception.BizException;
import com.ai.assistant.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * 用户上下文控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/context")
@RequiredArgsConstructor
public class ContextController {

    private final ContextService contextService;

    /**
     * 推送用户上下文
     */
    @PostMapping("/push")
    public ResponseEntity<ApiResponse<PushContextResponse>> pushContext(
            @Valid @RequestBody PushContextRequest request) {

        log.info("Received context push for session: {}, system: {}",
                request.getSessionId(), request.getSystemId());

        contextService.pushContext(request);

        PushContextResponse response = PushContextResponse.builder()
            .sessionId(request.getSessionId())
            .expiresAt(contextService.getSessionExpiresAt(request.getSessionId()))
            .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    /**
     * 获取会话的用户上下文
     */
    @GetMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<UserContextDTO>> getContext(
            @PathVariable String sessionId) {

        UserContextDTO context = contextService.getContext(sessionId);
        if (context == null) {
            throw new BizException("SESSION_NOT_FOUND",
                "Session not found or expired: " + sessionId);
        }

        // 不返回敏感信息
        context.setAccessToken(null);

        return ResponseEntity.ok(ApiResponse.success(context));
    }

    /**
     * 清除会话的用户上下文
     */
    @DeleteMapping("/{sessionId}")
    public ResponseEntity<ApiResponse<Void>> clearContext(
            @PathVariable String sessionId) {

        contextService.clearContext(sessionId);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 验证会话是否有效
     */
    @GetMapping("/{sessionId}/valid")
    public ResponseEntity<ApiResponse<SessionValidResponse>> validateSession(
            @PathVariable String sessionId) {

        boolean valid = contextService.isSessionValid(sessionId);
        return ResponseEntity.ok(ApiResponse.success(
            SessionValidResponse.builder()
                .sessionId(sessionId)
                .valid(valid)
                .build()
        ));
    }
}
