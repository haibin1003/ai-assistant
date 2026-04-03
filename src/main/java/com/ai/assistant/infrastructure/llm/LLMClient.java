package com.ai.assistant.infrastructure.llm;

import com.ai.assistant.domain.entity.Message;
import com.ai.assistant.infrastructure.llm.dto.LLMResponse;
import com.ai.assistant.infrastructure.mcp.dto.ToolDefinition;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;

/**
 * LLM 客户端接口
 */
public interface LLMClient {

    /**
     * 同步对话
     */
    LLMResponse chat(String systemPrompt, List<Message> history, List<ToolDefinition> tools);

    /**
     * 流式对话
     */
    Flux<String> streamChat(String systemPrompt, List<Message> history, List<ToolDefinition> tools);
}
