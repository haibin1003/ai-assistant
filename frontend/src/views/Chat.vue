<template>
  <div class="chat-container">
    <!-- 顶部导航 -->
    <header class="chat-header">
      <div class="header-left">
        <h1>AI 助手</h1>
      </div>
      <nav class="header-nav">
        <router-link to="/chat" class="nav-link active">对话</router-link>
        <router-link to="/settings" class="nav-link">配置</router-link>
      </nav>
      <div class="header-right">
        <div v-if="isLoggedIn" class="user-info">
          <span class="username">{{ username }}</span>
          <span class="system-tag">{{ currentSystem }}</span>
          <button @click="logout" class="btn-logout">退出</button>
        </div>
        <div v-else class="guest-mode">
          <span class="guest-badge">游客模式</span>
          <button @click="showLoginDialog = true" class="btn-login">登录系统</button>
        </div>
      </div>
    </header>

    <div class="chat-body">
      <!-- 左侧系统列表 -->
      <aside class="system-sidebar">
        <div class="sidebar-header">
          <h3>已接入系统</h3>
        </div>
        <div class="system-list">
          <div
            v-for="sys in systems"
            :key="sys.systemId"
            class="system-item"
            :class="{ active: selectedSystem === sys.systemId }"
            @click="selectSystem(sys.systemId)"
          >
            <div class="system-icon">{{ sys.systemName.charAt(0) }}</div>
            <div class="system-info">
              <span class="system-name">{{ sys.systemName }}</span>
              <span class="system-auth">{{ sys.authType === 'none' ? '公开' : '需认证' }}</span>
            </div>
          </div>
        </div>
        <div class="sidebar-footer">
          <button @click="useAsGuest" :class="['btn-guest', { active: !selectedSystem }]">
            不登录使用
          </button>
        </div>
      </aside>

      <!-- 主聊天区域 -->
      <main class="chat-main">
        <!-- 消息列表 -->
        <div class="messages-container" ref="messagesContainer">
          <div v-if="storedMessages.length === 0 && !currentAssistantMessage" class="empty-state">
            <div class="empty-icon">💬</div>
            <p>{{ isLoggedIn ? '开始新的对话' : '游客模式下只能使用公开工具' }}</p>
          </div>

          <!-- 已存储的消息 -->
          <div
            v-for="msg in storedMessages"
            :key="msg.id"
            class="message-row"
            :class="{
              'user-row': msg.role === 'user',
              'assistant-row': msg.role === 'assistant'
            }"
          >
            <!-- AI 消息在左侧 -->
            <div v-if="msg.role === 'assistant'" class="message assistant">
              <div class="message-avatar">🤖</div>
              <div class="message-content-wrapper">
                <div v-if="msg.thinking" class="thinking-block" v-html="formatContent(msg.thinking)"></div>
                <div v-if="msg.content" class="message-content">
                  <!-- 如果有工具结果中的下载信息，显示下载按钮 -->
                  <div v-if="msg.toolResult?.success && msg.toolResult?.data?.downloadUrl" class="doc-result">
                    <div class="doc-info" v-html="formatContent(msg.content)"></div>
                    <button class="download-btn" @click="downloadFile(msg.toolResult.data.downloadUrl)">
                      <span class="download-icon">📥</span>
                      <span>下载</span>
                    </button>
                  </div>
                  <div v-else v-html="formatContent(msg.content)"></div>
                </div>
              </div>
            </div>

            <!-- 用户消息在右侧 -->
            <div v-if="msg.role === 'user'" class="message user">
              <div class="message-content">
                <div class="message-text" v-html="formatContent(msg.content)"></div>
              </div>
              <div class="message-avatar">👤</div>
            </div>
          </div>

          <!-- 当前正在生成的 AI 消息 -->
          <div v-if="currentAssistantMessage" class="message-row assistant-row">
            <div class="message assistant">
              <div class="message-avatar">🤖</div>
              <div class="message-content-wrapper">
                <div v-if="currentAssistantMessage.thinking" class="thinking-block" v-html="formatContent(currentAssistantMessage.thinking)"></div>
                <div v-if="currentAssistantMessage.content" class="message-content">
                  <!-- 如果有工具结果中的下载信息，显示下载按钮 -->
                  <div v-if="currentAssistantMessage.toolResult?.success && currentAssistantMessage.toolResult?.data?.downloadUrl" class="doc-result">
                    <div class="doc-info" v-html="formatContent(currentAssistantMessage.content)"></div>
                    <button class="download-btn" @click="downloadFile(currentAssistantMessage.toolResult.data.downloadUrl)">
                      <span class="download-icon">📥</span>
                      <span>下载</span>
                    </button>
                  </div>
                  <div v-else v-html="formatContent(currentAssistantMessage.content)"></div>
                </div>
                <div v-if="!currentAssistantMessage.content && !currentAssistantMessage.thinking" class="typing-indicator">
                  <span></span><span></span><span></span>
                </div>
              </div>
            </div>
          </div>

          <!-- 加载状态 -->
          <div v-if="isLoading && !currentAssistantMessage" class="message-row assistant-row">
            <div class="message assistant">
              <div class="message-avatar">🤖</div>
              <div class="message-content">
                <div class="typing-indicator">
                  <span></span><span></span><span></span>
                </div>
              </div>
            </div>
          </div>
        </div>

        <!-- 输入区域 -->
        <div class="input-container">
          <textarea
            v-model="inputText"
            @keydown.enter.exact.prevent="sendMessage"
            placeholder="输入消息... (Enter 发送, Shift+Enter 换行)"
            rows="1"
            ref="inputArea"
          ></textarea>
          <button
            class="btn-send"
            @click="sendMessage"
            :disabled="!inputText.trim() || isLoading"
          >
            发送
          </button>
        </div>
      </main>
    </div>

    <!-- 登录对话框 -->
    <div v-if="showLoginDialog" class="dialog-overlay" @click.self="showLoginDialog = false">
      <div class="dialog">
        <h2>登录系统</h2>
        <div class="form-group">
          <label>选择系统</label>
          <select v-model="loginForm.systemId">
            <option value="osrm">OSRM</option>
          </select>
        </div>
        <div class="form-group">
          <label>用户名</label>
          <input v-model="loginForm.username" type="text" placeholder="请输入用户名" />
        </div>
        <div class="form-group">
          <label>密码</label>
          <input v-model="loginForm.password" type="password" placeholder="请输入密码" />
        </div>
        <div class="dialog-actions">
          <button @click="showLoginDialog = false" class="btn-cancel">取消</button>
          <button @click="login" class="btn-confirm">登录</button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref, computed, onMounted, nextTick } from 'vue'
import { useAssistantStore, type Message } from '../stores/assistant'
import api from '../api'

const store = useAssistantStore()

// State
const inputText = ref('')
const messagesContainer = ref<HTMLElement | null>(null)
const inputArea = ref<HTMLTextAreaElement | null>(null)
const currentId = ref<string | null>(null)
const selectedSystem = ref<string>('osrm')
const isLoggedIn = ref(false)
const username = ref('')
const currentSystem = ref('OSRM')
const showLoginDialog = ref(false)
const systems = ref<any[]>([])

// 当前正在生成的 AI 消息（使用本地响应式状态避免响应式问题）
const currentAssistantMessage = ref<{
  content: string
  thinking: string
  toolName?: string
  toolResult?: {
    success: boolean
    data?: {
      downloadUrl?: string
      fileName?: string
      title?: string
      fileSize?: number
    }
  }
} | null>(null)

const loginForm = ref({
  systemId: 'osrm',
  username: '',
  password: ''
})

// 使用计算属性获取已存储的消息
const storedMessages = computed(() => store.messages)
const isLoading = computed(() => store.isLoading)

// Methods
async function fetchSystems() {
  try {
    const res = await api.get('/systems')
    systems.value = res.data.data || []
  } catch (e) {
    console.error('Failed to fetch systems:', e)
  }
}

function selectSystem(systemId: string) {
  selectedSystem.value = systemId
  const sys = systems.value.find(s => s.systemId === systemId)
  if (sys) {
    currentSystem.value = sys.systemName
  }
  if (isLoggedIn.value) {
    store.setSession(store.sessionId, systemId)
  }
}

function useAsGuest() {
  selectedSystem.value = ''
  currentSystem.value = '公开模式'
  store.clearSession()
  currentAssistantMessage.value = null
}

async function login() {
  if (!loginForm.value.username || !loginForm.value.password) {
    return
  }

  try {
    // 验证用户密码并获取 token
    const res = await api.post('/auth/login', {
      username: loginForm.value.username,
      password: loginForm.value.password
    })

    const token = res.data.data?.token
    if (!token) {
      alert('登录失败：无效的响应')
      return
    }

    // 创建 session 并推送上下文
    const sessionId = 'session-' + Date.now()
    sessionStorage.setItem('sessionId', sessionId)

    await api.post('/context/push', {
      sessionId,
      systemId: loginForm.value.systemId,
      user: {
        id: res.data.data?.userId,
        username: loginForm.value.username,
        roles: res.data.data?.roles ? [res.data.data.roles] : ['DEVELOPER']
      },
      credentials: {
        accessToken: token,
        username: loginForm.value.username,
        password: loginForm.value.password
      }
    })

    // 设置登录状态
    isLoggedIn.value = true
    username.value = loginForm.value.username
    store.setSession(sessionId, loginForm.value.systemId)
    showLoginDialog.value = false
    currentSystem.value = 'OSRM'
    loginForm.value.username = ''
    loginForm.value.password = ''

  } catch (e: any) {
    alert('登录失败：' + (e.response?.data?.message || e.message))
  }
}

function logout() {
  isLoggedIn.value = false
  username.value = ''
  store.clearSession()
  currentAssistantMessage.value = null
}

function formatContent(content: string): string {
  if (!content) return ''
  return content
    // Headers ## Header -> styled span
    .replace(/^## (.+)$/gm, '<div class="md-header">$1</div>')
    .replace(/^### (.+)$/gm, '<div class="md-subheader">$1</div>')
    // Bold and italic
    .replace(/\*\*(.*?)\*\*/g, '<strong>$1</strong>')
    .replace(/\*(.*?)\*/g, '<em>$1</em>')
    // Code blocks
    .replace(/```([\s\S]*?)```/g, '<pre class="md-code">$1</pre>')
    .replace(/`([^`]+)`/g, '<code class="md-inline-code">$1</code>')
    // Lists
    .replace(/^- (.+)$/gm, '<div class="md-list-item">• $1</div>')
    .replace(/^\d+\. (.+)$/gm, '<div class="md-list-item">$1</div>')
    // Line breaks
    .replace(/\n/g, '<br>')
}

// 下载文件
function downloadFile(url: string) {
  const fullUrl = url.startsWith('http') ? url : window.location.origin + url
  window.open(fullUrl, '_blank')
}

function scrollToBottom() {
  nextTick(() => {
    if (messagesContainer.value) {
      messagesContainer.value.scrollTop = messagesContainer.value.scrollHeight
    }
  })
}

async function sendMessage() {
  const content = inputText.value.trim()
  if (!content) return

  // 确保有有效的 session
  if (!store.sessionId) {
    const sessionId = 'session-' + Date.now()
    sessionStorage.setItem('sessionId', sessionId)
    try {
      await api.post('/context/push', {
        sessionId,
        systemId: selectedSystem.value || 'osrm',
        user: {
          id: 0,
          username: 'guest',
          roles: ['GUEST']
        },
        credentials: {}
      })
      store.setSession(sessionId, selectedSystem.value || 'osrm')
    } catch (e) {
      console.error('Failed to create session:', e)
    }
  }

  // 添加用户消息到 store
  const userMsg: Message = {
    id: Date.now().toString(),
    role: 'user',
    content,
    createdAt: new Date().toISOString()
  }
  store.addMessage(userMsg)

  // 清空输入框
  inputText.value = ''
  store.setLoading(true)

  // 初始化当前 AI 消息（本地状态）
  currentAssistantMessage.value = {
    content: '',
    thinking: ''
  }
  scrollToBottom()

  try {
    const response = await fetch(`${store.config.apiUrl}/api/v1/chat`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json; charset=utf-8',
        'X-Session-Id': store.sessionId
      },
      body: JSON.stringify({
        content,
        conversationId: currentId.value || undefined
      })
    })

    if (!response.ok) {
      throw new Error('请求失败')
    }

    const reader = response.body?.getReader()
    const decoder = new TextDecoder()

    if (!reader) {
      throw new Error('无法读取响应')
    }

    let lineBuffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      const chunk = decoder.decode(value)
      lineBuffer += chunk
      const lines = lineBuffer.split('\n')
      // Keep the last incomplete line in buffer
      lineBuffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('data:')) {
          try {
            const dataStr = line.substring(5).trim()
            if (!dataStr) continue

            const data = JSON.parse(dataStr)
            const eventType = data.event
            const eventData = data.data

            // 处理各种事件类型
            if (eventType === 'thinking') {
              currentAssistantMessage.value!.thinking += (eventData?.message || '')
              scrollToBottom()
            } else if (eventType === 'reasoning') {
              currentAssistantMessage.value!.thinking += '\n' + (eventData?.thought || '')
              scrollToBottom()
            } else if (eventType === 'tool_use') {
              const toolName = eventData?.tool || ''
              currentAssistantMessage.value!.thinking += `\n🔧 正在调用工具: ${toolName}`
              currentAssistantMessage.value!.toolName = toolName
              scrollToBottom()
            } else if (eventType === 'tool_result') {
              const success = eventData?.success
              const resultData = eventData?.data
              // 保存工具结果信息，用于显示下载按钮
              currentAssistantMessage.value!.toolResult = {
                success,
                data: resultData
              }
              // 显示简洁的成功/失败消息
              if (success && resultData?.downloadUrl) {
                currentAssistantMessage.value!.thinking += `\n✅ 文档已生成: ${resultData.title || resultData.fileName || '文件'}`
              } else if (!success) {
                currentAssistantMessage.value!.thinking += `\n❌ 文档生成失败`
              }
              scrollToBottom()
            } else if (eventType === 'content') {
              const content = eventData?.content
              if (content) {
                currentAssistantMessage.value!.content += content
                scrollToBottom()
              }
            } else if (eventType === 'done') {
              // 完成，将消息保存到 store
              if (currentAssistantMessage.value) {
                const finalMsg: Message = {
                  id: (Date.now() + 1).toString(),
                  role: 'assistant',
                  content: currentAssistantMessage.value.content,
                  thinking: currentAssistantMessage.value.thinking,
                  toolName: currentAssistantMessage.value.toolName,
                  toolResult: currentAssistantMessage.value.toolResult,
                  createdAt: new Date().toISOString()
                }
                store.addMessage(finalMsg)
                currentAssistantMessage.value = null
              }
            }
          } catch (e) {
            // 静默忽略解析错误，继续处理下一行
          }
        }
      }
    }

    // 确保最终状态
    if (currentAssistantMessage.value && currentAssistantMessage.value.content) {
      const finalMsg: Message = {
        id: (Date.now() + 1).toString(),
        role: 'assistant',
        content: currentAssistantMessage.value.content,
        thinking: currentAssistantMessage.value.thinking,
        toolName: currentAssistantMessage.value.toolName,
        createdAt: new Date().toISOString()
      }
      store.addMessage(finalMsg)
      currentAssistantMessage.value = null
    }

    scrollToBottom()
  } catch (e: any) {
    console.error('Chat error:', e)
    store.setError(e.message)
    currentAssistantMessage.value = null
  } finally {
    store.setLoading(false)
  }
}

onMounted(async () => {
  await store.initConfig()
  await fetchSystems()
})
</script>

<style scoped>
.chat-container {
  display: flex;
  flex-direction: column;
  height: 100vh;
  background: #f5f7fa;
}

.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 0 20px;
  height: 60px;
  background: #fff;
  border-bottom: 1px solid #e4e7ed;
  box-shadow: 0 2px 4px rgba(0, 0, 0, 0.05);
}

.header-left h1 {
  font-size: 20px;
  font-weight: 600;
  color: #303133;
  margin: 0;
}

.header-nav {
  display: flex;
  gap: 20px;
}

.nav-link {
  text-decoration: none;
  color: #606266;
  font-size: 14px;
  padding: 8px 16px;
  border-radius: 4px;
  transition: all 0.2s;
}

.nav-link:hover {
  background: #f5f7fa;
  color: #409eff;
}

.nav-link.active {
  background: #ecf5ff;
  color: #409eff;
}

.header-right {
  display: flex;
  align-items: center;
  gap: 12px;
}

.user-info {
  display: flex;
  align-items: center;
  gap: 12px;
}

.username {
  font-size: 14px;
  color: #303133;
}

.system-tag {
  font-size: 12px;
  padding: 2px 8px;
  background: #f0f9ff;
  color: #409eff;
  border-radius: 4px;
}

.btn-logout {
  padding: 6px 16px;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  color: #606266;
  transition: all 0.2s;
}

.btn-logout:hover {
  color: #f56c6c;
  border-color: #f56c6c;
}

.guest-mode {
  display: flex;
  align-items: center;
  gap: 12px;
}

.guest-badge {
  font-size: 12px;
  padding: 4px 10px;
  background: #f4f4f5;
  color: #909399;
  border-radius: 4px;
}

.btn-login {
  padding: 6px 16px;
  background: #409eff;
  border: none;
  border-radius: 4px;
  cursor: pointer;
  font-size: 13px;
  color: #fff;
  transition: all 0.2s;
}

.btn-login:hover {
  background: #66b1ff;
}

.chat-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.system-sidebar {
  width: 220px;
  background: #fff;
  border-right: 1px solid #e4e7ed;
  display: flex;
  flex-direction: column;
}

.sidebar-header {
  padding: 16px;
  border-bottom: 1px solid #e4e7ed;
}

.sidebar-header h3 {
  margin: 0;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}

.system-list {
  flex: 1;
  overflow-y: auto;
  padding: 8px;
}

.system-item {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 12px;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s;
}

.system-item:hover {
  background: #f5f7fa;
}

.system-item.active {
  background: #ecf5ff;
}

.system-icon {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  border-radius: 8px;
  display: flex;
  align-items: center;
  justify-content: center;
  color: #fff;
  font-weight: 600;
  font-size: 16px;
}

.system-info {
  display: flex;
  flex-direction: column;
  gap: 2px;
}

.system-name {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
}

.system-auth {
  font-size: 12px;
  color: #909399;
}

.sidebar-footer {
  padding: 16px;
  border-top: 1px solid #e4e7ed;
}

.btn-guest {
  width: 100%;
  padding: 10px;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  cursor: pointer;
  font-size: 13px;
  color: #606266;
  transition: all 0.2s;
}

.btn-guest:hover,
.btn-guest.active {
  background: #f0f9ff;
  border-color: #409eff;
  color: #409eff;
}

.chat-main {
  flex: 1;
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.messages-container {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  display: flex;
  flex-direction: column;
  gap: 16px;
}

.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  color: #909399;
}

.empty-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.message-row {
  display: flex;
  align-items: flex-start;
}

.user-row {
  justify-content: flex-end;
}

.assistant-row {
  justify-content: flex-start;
}

.message {
  display: flex;
  gap: 12px;
  max-width: 70%;
}

.user .message-content {
  background: #409eff;
  color: #fff;
  border-radius: 16px 16px 4px 16px;
}

.assistant .message-content {
  background: #fff;
  border-radius: 16px 16px 16px 4px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.08);
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  font-size: 20px;
  flex-shrink: 0;
}

.message-content {
  padding: 12px 16px;
  font-size: 14px;
  line-height: 1.6;
}

.thinking-block {
  color: #909399;
  font-size: 13px;
  margin-bottom: 8px;
  white-space: pre-wrap;
  word-break: break-word;
}

.message-text {
  white-space: pre-wrap;
  word-break: break-word;
}

/* Document result display */
.doc-result {
  display: flex;
  align-items: flex-start;
  gap: 16px;
  padding: 12px;
  background: #f0f9ff;
  border-radius: 8px;
  border: 1px solid #e1f3ff;
}

.doc-info {
  flex: 1;
}

.download-btn {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 16px;
  background: #409eff;
  color: #fff;
  border: none;
  border-radius: 6px;
  cursor: pointer;
  font-size: 14px;
  transition: all 0.2s;
  white-space: nowrap;
}

.download-btn:hover {
  background: #66b1ff;
}

.download-icon {
  font-size: 16px;
}

/* Markdown formatting styles */
.md-header {
  font-size: 18px;
  font-weight: 600;
  color: #303133;
  margin: 16px 0 8px 0;
  padding-bottom: 4px;
  border-bottom: 1px solid #e4e7ed;
}

.md-subheader {
  font-size: 16px;
  font-weight: 600;
  color: #606266;
  margin: 12px 0 6px 0;
}

.md-code {
  background: #f5f7fa;
  padding: 12px;
  border-radius: 4px;
  font-family: monospace;
  font-size: 13px;
  overflow-x: auto;
  margin: 8px 0;
}

.md-inline-code {
  background: #f5f7fa;
  padding: 2px 6px;
  border-radius: 3px;
  font-family: monospace;
  font-size: 13px;
  color: #e6a23c;
}

.md-list-item {
  padding: 4px 0;
  color: #606266;
}

.typing-indicator {
  display: flex;
  gap: 4px;
  padding: 8px;
}

.typing-indicator span {
  width: 8px;
  height: 8px;
  background: #909399;
  border-radius: 50%;
  animation: typing 1.4s infinite;
}

.typing-indicator span:nth-child(2) {
  animation-delay: 0.2s;
}

.typing-indicator span:nth-child(3) {
  animation-delay: 0.4s;
}

@keyframes typing {
  0%, 60%, 100% {
    transform: translateY(0);
  }
  30% {
    transform: translateY(-8px);
  }
}

.input-container {
  display: flex;
  gap: 12px;
  padding: 16px 20px;
  background: #fff;
  border-top: 1px solid #e4e7ed;
}

.input-container textarea {
  flex: 1;
  padding: 12px 16px;
  border: 1px solid #dcdfe6;
  border-radius: 8px;
  font-size: 14px;
  resize: none;
  font-family: inherit;
  outline: none;
  transition: border-color 0.2s;
}

.input-container textarea:focus {
  border-color: #409eff;
}

.btn-send {
  padding: 10px 24px;
  background: #409eff;
  border: none;
  border-radius: 8px;
  color: #fff;
  font-size: 14px;
  cursor: pointer;
  transition: all 0.2s;
}

.btn-send:hover:not(:disabled) {
  background: #66b1ff;
}

.btn-send:disabled {
  background: #a0cfff;
  cursor: not-allowed;
}

.dialog-overlay {
  position: fixed;
  top: 0;
  left: 0;
  right: 0;
  bottom: 0;
  background: rgba(0, 0, 0, 0.5);
  display: flex;
  align-items: center;
  justify-content: center;
  z-index: 1000;
}

.dialog {
  background: #fff;
  border-radius: 12px;
  padding: 24px;
  width: 400px;
  box-shadow: 0 4px 20px rgba(0, 0, 0, 0.15);
}

.dialog h2 {
  margin: 0 0 20px;
  font-size: 18px;
  color: #303133;
}

.form-group {
  margin-bottom: 16px;
}

.form-group label {
  display: block;
  margin-bottom: 8px;
  font-size: 14px;
  color: #606266;
}

.form-group input,
.form-group select {
  width: 100%;
  padding: 10px 12px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  font-size: 14px;
  outline: none;
  box-sizing: border-box;
}

.form-group input:focus,
.form-group select:focus {
  border-color: #409eff;
}

.dialog-actions {
  display: flex;
  justify-content: flex-end;
  gap: 12px;
  margin-top: 24px;
}

.btn-cancel {
  padding: 10px 20px;
  background: #fff;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  color: #606266;
  cursor: pointer;
  font-size: 14px;
}

.btn-confirm {
  padding: 10px 20px;
  background: #409eff;
  border: none;
  border-radius: 6px;
  color: #fff;
  cursor: pointer;
  font-size: 14px;
}
</style>