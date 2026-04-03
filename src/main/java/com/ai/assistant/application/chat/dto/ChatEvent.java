package com.ai.assistant.application.chat.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.HashMap;
import java.util.Map;

/**
 * 对话事件 (SSE)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChatEvent {

    private String event;
    private Object data;

    public static final String EVENT_CONTENT = "content";
    public static final String EVENT_TOOL_USE = "tool_use";
    public static final String EVENT_TOOL_RESULT = "tool_result";
    public static final String EVENT_ERROR = "error";
    public static final String EVENT_DONE = "done";
    public static final String EVENT_THINKING = "thinking";  // AI 思考中
    public static final String EVENT_REASONING = "reasoning"; // 推理过程

    public static ChatEvent content(String content) {
        return ChatEvent.builder()
            .event(EVENT_CONTENT)
            .data(Map.of("content", content))
            .build();
    }

    public static ChatEvent thinking(String message) {
        return ChatEvent.builder()
            .event(EVENT_THINKING)
            .data(Map.of("message", message))
            .build();
    }

    public static ChatEvent reasoning(String thought) {
        return ChatEvent.builder()
            .event(EVENT_REASONING)
            .data(Map.of("thought", thought))
            .build();
    }

    public static ChatEvent toolUse(String tool, Map<String, Object> args) {
        return ChatEvent.builder()
            .event(EVENT_TOOL_USE)
            .data(Map.of("tool", tool, "args", args))
            .build();
    }

    public static ChatEvent toolResult(boolean success, Object result) {
        Map<String, Object> data = new HashMap<>();
        data.put("success", success);
        data.put("data", result != null ? result : "");
        return ChatEvent.builder()
            .event(EVENT_TOOL_RESULT)
            .data(data)
            .build();
    }

    public static ChatEvent error(String code, String message) {
        return ChatEvent.builder()
            .event(EVENT_ERROR)
            .data(Map.of("code", code, "message", message))
            .build();
    }

    public static ChatEvent done() {
        return ChatEvent.builder()
            .event(EVENT_DONE)
            .data(Map.of())
            .build();
    }
}
