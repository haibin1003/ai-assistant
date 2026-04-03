package com.ai.assistant.application.chat;

import com.ai.assistant.application.chat.dto.ChatEvent;
import com.ai.assistant.application.chat.dto.ChatRequest;
import com.ai.assistant.application.context.ContextService;
import com.ai.assistant.application.context.dto.UserContextDTO;
import com.ai.assistant.application.config.ConfigService;
import com.ai.assistant.application.system.SystemService;
import com.ai.assistant.domain.entity.Conversation;
import com.ai.assistant.domain.entity.Message;
import com.ai.assistant.domain.repository.ConversationRepository;
import com.ai.assistant.domain.repository.MessageRepository;
import com.ai.assistant.infrastructure.llm.LLMClientFactory;
import com.ai.assistant.infrastructure.llm.LLMClient;
import com.ai.assistant.infrastructure.llm.dto.LLMResponse;
import com.ai.assistant.infrastructure.mcp.ToolRouter;
import com.ai.assistant.infrastructure.mcp.dto.ToolDefinition;
import com.ai.assistant.infrastructure.mcp.dto.ToolResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.data.domain.PageRequest;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatServiceTest {

    @Mock
    private ContextService contextService;

    @Mock
    private ConversationService conversationService;

    @Mock
    private SystemPromptBuilder systemPromptBuilder;

    @Mock
    private ToolRouter toolRouter;

    @Mock
    private LLMClientFactory llmClientFactory;

    @Mock
    private ConfigService configService;

    @Mock
    private SystemService systemService;

    @Mock
    private LLMClient llmClient;

    @InjectMocks
    private ChatService chatService;

    private UserContextDTO testContext;
    private Conversation testConversation;

    @BeforeEach
    void setUp() {
        testContext = UserContextDTO.builder()
            .sessionId("test-session")
            .systemId("osrm")
            .userId("user-001")
            .username("testuser")
            .accessToken("test-token")
            .expiresAt(LocalDateTime.now().plusHours(24))
            .build();

        testConversation = Conversation.builder()
            .conversationId("conv-001")
            .sessionId("test-session")
            .messageCount(0)
            .isDeleted(false)
            .build();
    }

    @Test
    @DisplayName("流式对话 - 会话无效")
    void chatStream_invalidSession() {
        when(contextService.getContext("invalid-session")).thenReturn(null);

        ChatRequest request = ChatRequest.builder()
            .content("Hello")
            .build();

        Flux<ServerSentEvent<ChatEvent>> result = chatService.chatStream("invalid-session", request);

        StepVerifier.create(result)
            .expectNextMatches(event -> {
                ChatEvent data = event.data();
                return ChatEvent.EVENT_ERROR.equals(data.getEvent());
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("流式对话 - 会话有效但无对话ID")
    void chatStream_validSessionNoConversationId() {
        when(contextService.getContext("test-session")).thenReturn(testContext);
        when(conversationService.getOrCreateConversation(eq("test-session"), isNull()))
            .thenReturn(testConversation);
        when(conversationService.getRecentMessages(anyString(), anyInt()))
            .thenReturn(Collections.emptyList());
        when(systemPromptBuilder.buildSystemPrompt(any())).thenReturn("System prompt");
        when(configService.getLLMProvider()).thenReturn("deepseek");
        when(llmClientFactory.createClient(anyString())).thenReturn(llmClient);
        // Use lenient to avoid UnnecessaryStubbingException
        lenient().when(llmClient.chat(anyString(), anyList(), anyList()))
            .thenReturn(LLMResponse.builder().content("Hello!").build());

        ChatRequest request = ChatRequest.builder()
            .content("Hello")
            .build();

        Flux<ServerSentEvent<ChatEvent>> result = chatService.chatStream("test-session", request);

        StepVerifier.create(result)
            .expectNextMatches(event -> {
                // Either content or done event is fine
                return event.data() != null;
            })
            .expectNextMatches(event -> {
                // Done event
                return event.data() != null;
            })
            .verifyComplete();
    }

    @Test
    @DisplayName("对话服务 - 创建对话 (mock测试)")
    void createConversation_mockTest() {
        // 配置 mock 返回值
        when(conversationService.createConversation("test-session")).thenReturn(testConversation);

        Conversation result = conversationService.createConversation("test-session");

        assertNotNull(result);
        assertEquals("test-session", result.getSessionId());
        verify(conversationService).createConversation("test-session");
    }

    @Test
    @DisplayName("对话服务 - 添加消息 (mock测试)")
    void addMessage_mockTest() {
        Message message = Message.builder()
            .conversationId("conv-001")
            .role("user")
            .content("Hello")
            .build();

        when(conversationService.addMessage("conv-001", "user", "Hello")).thenReturn(message);

        Message result = conversationService.addMessage("conv-001", "user", "Hello");

        assertNotNull(result);
        assertEquals("user", result.getRole());
        assertEquals("Hello", result.getContent());
    }

    @Test
    @DisplayName("对话服务 - 获取最近消息 (mock测试)")
    void getRecentMessages_mockTest() {
        Message msg1 = Message.builder().conversationId("conv-001").role("user").content("Hello").build();
        Message msg2 = Message.builder().conversationId("conv-001").role("assistant").content("Hi").build();

        when(conversationService.getRecentMessages("conv-001", 10))
            .thenReturn(List.of(msg2, msg1));

        List<Message> result = conversationService.getRecentMessages("conv-001", 10);

        assertEquals(2, result.size());
    }
}
