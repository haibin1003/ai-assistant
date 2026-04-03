package com.ai.assistant.application.config;

import com.ai.assistant.domain.entity.ApiKeyConfig;
import com.ai.assistant.domain.repository.ApiKeyConfigRepository;
import com.ai.assistant.infrastructure.security.EncryptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ai.assistant.application.config.dto.ApiKeyConfigDTO;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 配置服务
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ConfigService {

    private final ApiKeyConfigRepository apiKeyConfigRepository;
    private final EncryptionService encryptionService;

    // 缓存
    private final Map<String, String> apiKeyCache = new ConcurrentHashMap<>();

    @Value("${ai-assistant.llm.default-provider:deepseek}")
    private String defaultLLMProvider;

    @Value("${ai-assistant.search.default-provider:serper}")
    private String defaultSearchProvider;

    /**
     * 设置 API Key
     */
    @Transactional
    public void setApiKey(String provider, String providerType, String apiKey, String apiEndpoint) {
        Optional<ApiKeyConfig> existing = apiKeyConfigRepository.findByProvider(provider);

        String encryptedKey = encryptionService.encrypt(apiKey);

        ApiKeyConfig config;
        if (existing.isPresent()) {
            config = existing.get();
            config.setApiKey(encryptedKey);
            if (apiEndpoint != null) {
                config.setApiEndpoint(apiEndpoint);
            }
            config.setIsActive(true);
        } else {
            config = ApiKeyConfig.builder()
                .provider(provider)
                .providerType(providerType)
                .apiKey(encryptedKey)
                .apiEndpoint(apiEndpoint)
                .isActive(true)
                .build();
        }

        apiKeyConfigRepository.save(config);
        apiKeyCache.put(provider, apiKey);

        log.info("API Key configured for provider: {}", provider);
    }

    /**
     * 获取 API Key
     */
    public String getApiKey(String provider) {
        String cached = apiKeyCache.get(provider);
        if (cached != null) {
            return cached;
        }

        Optional<ApiKeyConfig> config = apiKeyConfigRepository.findByProviderAndIsActiveTrue(provider);
        if (config.isEmpty()) {
            return null;
        }

        String decryptedKey = encryptionService.decrypt(config.get().getApiKey());
        apiKeyCache.put(provider, decryptedKey);
        return decryptedKey;
    }

    /**
     * 获取 LLM 提供商
     */
    public String getLLMProvider() {
        if (getApiKey(defaultLLMProvider) != null) {
            return defaultLLMProvider;
        }

        String[] providers = {"openai", "claude"};
        for (String p : providers) {
            if (getApiKey(p) != null) {
                return p;
            }
        }

        return defaultLLMProvider;
    }

    /**
     * 获取搜索提供商
     */
    public String getSearchProvider() {
        if (getApiKey(defaultSearchProvider) != null) {
            return defaultSearchProvider;
        }

        if (getApiKey("tavily") != null) {
            return "tavily";
        }

        return defaultSearchProvider;
    }

    /**
     * 清除缓存
     */
    public void clearCache() {
        apiKeyCache.clear();
        log.info("API Key cache cleared");
    }

    /**
     * 获取所有 API Key 配置
     */
    public List<ApiKeyConfigDTO> getAllApiKeyConfigs() {
        return apiKeyConfigRepository.findAll().stream()
            .map(config -> ApiKeyConfigDTO.builder()
                .provider(config.getProvider())
                .providerType(config.getProviderType())
                .configured(config.getApiKey() != null && !config.getApiKey().isEmpty())
                .apiEndpoint(config.getApiEndpoint())
                .active(config.getIsActive())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build())
            .collect(Collectors.toList());
    }

    /**
     * 获取单个提供商的配置状态
     */
    public ApiKeyConfigDTO getApiKeyConfig(String provider) {
        return apiKeyConfigRepository.findByProvider(provider)
            .map(config -> ApiKeyConfigDTO.builder()
                .provider(config.getProvider())
                .providerType(config.getProviderType())
                .configured(config.getApiKey() != null && !config.getApiKey().isEmpty())
                .apiEndpoint(config.getApiEndpoint())
                .active(config.getIsActive())
                .createdAt(config.getCreatedAt())
                .updatedAt(config.getUpdatedAt())
                .build())
            .orElse(null);
    }

    /**
     * 删除 API Key
     */
    @Transactional
    public void deleteApiKey(String provider) {
        apiKeyConfigRepository.findByProvider(provider).ifPresent(config -> {
            config.setIsActive(false);
            apiKeyConfigRepository.save(config);
            apiKeyCache.remove(provider);
            log.info("API Key deactivated for provider: {}", provider);
        });
    }

    /**
     * 验证 API Key 是否有效
     */
    public boolean validateApiKey(String provider, String providerType) {
        String apiKey = getApiKey(provider);
        if (apiKey == null || apiKey.isEmpty()) {
            return false;
        }
        // 简单验证：检查密钥是否存在
        // 实际应用中可以调用 API 进行验证
        return true;
    }
}
