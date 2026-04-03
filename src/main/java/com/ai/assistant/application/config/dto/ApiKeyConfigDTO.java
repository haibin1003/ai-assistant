package com.ai.assistant.application.config.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * API Key 配置 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiKeyConfigDTO {

    private String provider;
    private String providerType;
    private Boolean configured;
    private Boolean active;
    private String apiEndpoint;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
