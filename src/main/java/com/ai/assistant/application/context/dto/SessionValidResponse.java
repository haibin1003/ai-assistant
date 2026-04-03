package com.ai.assistant.application.context.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 会话验证响应
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SessionValidResponse {
    private String sessionId;
    private Boolean valid;
}
