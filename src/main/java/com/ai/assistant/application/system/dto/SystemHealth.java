package com.ai.assistant.application.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 系统健康状态
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemHealth {

    private String systemId;
    private String status; // UP, DOWN
    private String error;
    private LocalDateTime checkedAt;
}
