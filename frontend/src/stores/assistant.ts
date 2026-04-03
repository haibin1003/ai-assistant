import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import api from '../api'

export interface Message {
  id: string
  role: 'user' | 'assistant' | 'tool'
  content: string
  toolName?: string
  createdAt: string
  thinking?: string    // AI 思考/推理过程
  toolResult?: {       // 工具执行结果
    success: boolean
    data?: {
      downloadUrl?: string
      fileName?: string
      title?: string
      fileSize?: number
    }
  }
}

export interface Conversation {
  conversationId: string
  title: string
  messageCount: number
  createdAt: string
  updatedAt: string
}

export const useAssistantStore = defineStore('assistant', () => {
  // State
  const sessionId = ref<string>('')
  const systemId = ref<string>('')
  const conversations = ref<Conversation[]>([])
  const currentConversation = ref<Conversation | null>(null)

  // 使用 shallowRef 避免深度响应式开销，手动触发更新
  const messages = ref<Message[]>([])
  const isLoading = ref(false)
  const error = ref<string | null>(null)
  const config = ref({
    apiUrl: '',
    wsUrl: ''
  })

  // Getters
  const hasSession = computed(() => !!sessionId.value)
  const hasMessages = computed(() => messages.value.length > 0)

  // Actions
  function setSession(sid: string, sysId: string) {
    sessionId.value = sid
    systemId.value = sysId
  }

  function clearSession() {
    sessionId.value = ''
    systemId.value = ''
    conversations.value = []
    currentConversation.value = null
    messages.value = []
  }

  async function initConfig() {
    config.value = {
      apiUrl: import.meta.env.VITE_API_URL || 'http://localhost:8081',
      wsUrl: import.meta.env.VITE_WS_URL || 'ws://localhost:8081'
    }
  }

  async function fetchConversations() {
    if (!sessionId.value) return

    try {
      const response = await api.get(`/conversations`, {
        headers: { 'X-Session-Id': sessionId.value }
      })
      conversations.value = response.data.data || []
    } catch (e: any) {
      error.value = e.message
    }
  }

  async function fetchConversation(conversationId: string) {
    if (!sessionId.value) return

    try {
      const response = await api.get(`/conversations/${conversationId}`, {
        headers: { 'X-Session-Id': sessionId.value }
      })
      currentConversation.value = response.data.data.conversation
      messages.value = response.data.data.messages || []
    } catch (e: any) {
      error.value = e.message
    }
  }

  async function deleteConversation(conversationId: string) {
    if (!sessionId.value) return

    try {
      await api.delete(`/conversations/${conversationId}`, {
        headers: { 'X-Session-Id': sessionId.value }
      })
      conversations.value = conversations.value.filter(
        c => c.conversationId !== conversationId
      )
      if (currentConversation.value?.conversationId === conversationId) {
        currentConversation.value = null
        messages.value = []
      }
    } catch (e: any) {
      error.value = e.message
    }
  }

  function addMessage(message: Message) {
    // 创建新数组以触发响应式更新
    messages.value = [...messages.value, message]
  }

  /**
   * 更新消息 - 确保触发响应式更新
   */
  function updateMessage(messageId: string, updates: Partial<Message>) {
    const index = messages.value.findIndex(m => m.id === messageId)
    if (index !== -1) {
      // 创建新数组并更新元素
      const newMessages = [...messages.value]
      newMessages[index] = { ...newMessages[index], ...updates }
      messages.value = newMessages
    }
  }

  function clearMessages() {
    messages.value = []
  }

  function setLoading(loading: boolean) {
    isLoading.value = loading
  }

  function setError(err: string | null) {
    error.value = err
  }

  return {
    // State
    sessionId,
    systemId,
    conversations,
    currentConversation,
    messages,
    isLoading,
    error,
    config,
    // Getters
    hasSession,
    hasMessages,
    // Actions
    setSession,
    clearSession,
    initConfig,
    fetchConversations,
    fetchConversation,
    deleteConversation,
    addMessage,
    updateMessage,
    clearMessages,
    setLoading,
    setError
  }
})