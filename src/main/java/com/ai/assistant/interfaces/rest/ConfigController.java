package com.ai.assistant.interfaces.rest;

import com.ai.assistant.application.config.ConfigService;
import com.ai.assistant.application.config.dto.ApiKeyConfigDTO;
import com.ai.assistant.application.config.dto.SetApiKeyRequest;
import com.ai.assistant.application.config.dto.ValidationResult;
import com.ai.assistant.common.response.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 配置管理控制器
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/config")
@RequiredArgsConstructor
public class ConfigController {

    private final ConfigService configService;

    /**
     * 获取 API Key 配置状态
     */
    @GetMapping("/api-key")
    public ResponseEntity<ApiResponse<List<ApiKeyConfigDTO>>> getApiKeyConfigs() {
        List<ApiKeyConfigDTO> configs = configService.getAllApiKeyConfigs();
        return ResponseEntity.ok(ApiResponse.success(configs));
    }

    /**
     * 获取单个提供商的配置状态
     */
    @GetMapping("/api-key/{provider}")
    public ResponseEntity<ApiResponse<ApiKeyConfigDTO>> getApiKeyConfig(
            @PathVariable String provider) {
        ApiKeyConfigDTO config = configService.getApiKeyConfig(provider);
        return ResponseEntity.ok(ApiResponse.success(config));
    }

    /**
     * 设置 API Key
     */
    @PutMapping("/api-key/{provider}")
    public ResponseEntity<ApiResponse<Void>> setApiKey(
            @PathVariable String provider,
            @Valid @RequestBody SetApiKeyRequest request) {

        configService.setApiKey(
            provider,
            request.getProviderType(),
            request.getApiKey(),
            request.getApiEndpoint()
        );

        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 删除 API Key
     */
    @DeleteMapping("/api-key/{provider}")
    public ResponseEntity<ApiResponse<Void>> deleteApiKey(
            @PathVariable String provider) {

        configService.deleteApiKey(provider);
        return ResponseEntity.ok(ApiResponse.success(null));
    }

    /**
     * 验证 API Key
     */
    @PostMapping("/api-key/{provider}/validate")
    public ResponseEntity<ApiResponse<ValidationResult>> validateApiKey(
            @PathVariable String provider,
            @RequestParam String providerType) {

        boolean valid = configService.validateApiKey(provider, providerType);

        return ResponseEntity.ok(ApiResponse.success(
            ValidationResult.builder()
                .provider(provider)
                .valid(valid)
                .build()
        ));
    }
}
