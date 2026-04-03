<template>
  <div class="chat-content">
    <!-- 消息列表 -->
    <div class="messages-container" ref="messagesContainer">
      <div v-if="messages.length === 0" class="empty-state">
        <div class="empty-icon">💬</div>
        <p>开始新的对话</p>
      </div>

      <div
        v-for="msg in messages"
        :key="msg.id"
        class="message"
        :class="msg.role"
      >
        <div class="message-content">
          <div class="message-text" v-html="formatContent(msg.content)"></div>
        </div>
      </div>

      <div v-if="isLoading" class="message assistant">
        <div class="message-content">
          <div class="typing-indicator">
            <span></span><span></span><span></span>
          </div>
        </div>
      </div>
    </div>

    <!-- 输入区域 -->
    <div class="input-container">
      <textarea
        v-model="inputText"
        @keydown.enter.exact.prevent="sendMessage"
        placeholder="输入消息..."
        rows="1"
      ></textarea>
      <button
        class="btn-send"
        @click="sendMessage"
        :disabled="!inputText.trim() || isLoading"
      >
        发送
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, nextTick } from 'vue'
import { useAssistantStore, type Message } from '../stores/assistant'

defineProps<{
  embedded?: boolean
}>()

const store = useAssistantStore()

const inputText = ref('')
const messagesContainer = ref<HTMLElement | null>(null)

const messages = computed(() => store.messages)
const isLoading = computed(() => store.isLoading)

async function sendMessage() {
  const content = inputText.value.trim()
  if (!content || !store.sessionId) return

  const userMsg: Message = {
    id: Date.now().toString(),
    role: 'user',
    content,
    createdAt: new Date().toISOString()
  }
  store.addMessage(userMsg)
  inputText.value = ''
  store.setLoading(true)
  scrollToBottom()

  try {
    const response = await fetch(`${store.config.apiUrl}/api/v1/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'X-Session-Id': store.sessionId
      },
      body: JSON.stringify({ content })
    })

    if (!response.ok) throw new Error('请求失败')

    const reader = response.body?.getReader()
    const decoder = new TextDecoder()

    let assistantContent = ''
    const assistantMsg: Message = {
      id: (Date.now() + 1).toString(),
      role: 'assistant',
      content: '',
      createdAt: new Date().toISOString()
    }
    store.addMessage(assistantMsg)

    while (reader) {
      const { done, value } = await reader.read()
      if (done) break

      const chunk = decoder.decode(value)
      const lines = chunk.split('\n')

      for (const line of lines) {
        if (line.startsWith('data:')) {
          try {
            const data = JSON.parse(line.substring(5))
            if (data.content) {
              assistantContent += data.content
              assistantMsg.content = assistantContent
              scrollToBottom()
            }
          } catch {
            // ignore
          }
        }
      }
    }

    scrollToBottom()
  } catch (e: any) {
    store.setError(e.message)
  } finally {
    store.setLoading(false)
  }
}

function formatContent(content: string): string {
  return content
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\n/g, '<br>')
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}
</script>

<style scoped>
.chat-content {
  height: 100%;
  display: flex;
  flex-direction: column;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 16px;
}

.empty-state {
  text-align: center;
  padding: 48px;
  color: #999;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.message {
  margin-bottom: 16px;
}

.message.user {
  text-align: right;
}

.message-content {
  display: inline-block;
  padding: 12px 16px;
  border-radius: 12px;
  max-width: 80%;
  text-align: left;
}

.message.user .message-content {
  background: #4a90d9;
  color: #fff;
}

.message.assistant .message-content {
  background: #f0f0f0;
  color: #333;
}

.message-text {
  line-height: 1.5;
  word-break: break-word;
}

.typing-indicator {
  display: flex;
  gap: 4px;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  background: #999;
  border-radius: 50%;
  animation: bounce 1.4s infinite ease-in-out;
}

.typing-indicator span:nth-child(1) { animation-delay: -0.32s; }
.typing-indicator span:nth-child(2) { animation-delay: -0.16s; }

@keyframes bounce {
  0%, 80%, 100% { transform: scale(0); }
  40% { transform: scale(1); }
}

.input-container {
  padding: 12px;
  background: #fff;
  border-top: 1px solid #e0e0e0;
  display: flex;
  gap: 8px;
}

.input-container textarea {
  flex: 1;
  padding: 10px;
  border: 1px solid #e0e0e0;
  border-radius: 8px;
  resize: none;
  font-size: 14px;
  font-family: inherit;
}

.input-container textarea:focus {
  outline: none;
  border-color: #4a90d9;
}

.btn-send {
  padding: 10px 20px;
  background: #4a90d9;
  color: #fff;
  border: none;
  border-radius: 8px;
  cursor: pointer;
}

.btn-send:hover:not(:disabled) {
  background: #3a7bc8;
}

.btn-send:disabled {
  background: #ccc;
  cursor: not-allowed;
}
</style>
