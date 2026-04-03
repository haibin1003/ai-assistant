package com.ai.assistant.application.system.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 已注册系统 DTO
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RegisteredSystemDTO {

    private String systemId;
    private String systemName;
    private String iconUrl;
    private String mcpGatewayUrl;
    private String authType;
    private String toolPrefix;
    private String description;
    private Boolean isActive;
    private Integer toolCount;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
