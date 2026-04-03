<template>
  <div class="chat-container embedded">
    <ChatContent embedded />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useAssistantStore } from '../stores/assistant'
import ChatContent from '../components/ChatContent.vue'

const store = useAssistantStore()

onMounted(() => {
  // 从 URL 参数获取系统 ID
  const urlParams = new URLSearchParams(window.location.search)
  const systemId = urlParams.get('system')
  if (systemId) {
    store.systemId = systemId
  }

  // 监听 postMessage
  window.addEventListener('message', (event) => {
    if (event.data?.type === 'USER_CONTEXT') {
      const { sessionId, systemId } = event.data.payload
      store.setSession(sessionId, systemId)
    }
  })
})
</script>

<style scoped>
.embedded {
  height: 100vh;
}
</style>
