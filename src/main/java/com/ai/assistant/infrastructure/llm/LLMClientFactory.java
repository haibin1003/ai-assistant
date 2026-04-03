package com.ai.assistant.infrastructure.llm;

import com.ai.assistant.application.config.ConfigService;
import org.springframework.stereotype.Component;

/**
 * LLM 客户端工厂
 */
@Component
public class LLMClientFactory {

    private final ConfigService configService;
    private final DeepSeekClient deepSeekClient;

    public LLMClientFactory(ConfigService configService, DeepSeekClient deepSeekClient) {
        this.configService = configService;
        this.deepSeekClient = deepSeekClient;
    }

    /**
     * 创建 LLM 客户端
     */
    public LLMClient createClient(String provider) {
        return switch (provider.toLowerCase()) {
            case "deepseek" -> deepSeekClient;
            // 后续可扩展 openai, claude
            default -> deepSeekClient;
        };
    }

    /**
     * 获取默认客户端
     */
    public LLMClient getDefaultClient() {
        String provider = configService.getLLMProvider();
        return createClient(provider);
    }
}
