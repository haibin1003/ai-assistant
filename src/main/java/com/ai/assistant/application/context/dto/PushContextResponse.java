package com.ai.assistant.application.context.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 推送上下文响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PushContextResponse {
    private String sessionId;
    private LocalDateTime expiresAt;
}
