package com.ai.assistant.application.config.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 设置 API Key 请求
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SetApiKeyRequest {

    @NotBlank(message = "提供商类型不能为空")
    private String providerType;

    @NotBlank(message = "API Key 不能为空")
    private String apiKey;

    private String apiEndpoint;
}
