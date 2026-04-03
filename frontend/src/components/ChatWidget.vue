<template>
  <div class="chat-widget" :class="[`position-${position}`, { open: isOpen }]">
    <!-- 触发按钮 -->
    <button
      v-if="!isOpen"
      class="trigger-btn"
      @click="open"
    >
      <span class="icon">💬</span>
    </button>

    <!-- 聊天窗口 -->
    <div v-else class="chat-panel">
      <div class="panel-header">
        <span class="title">AI 助手</span>
        <button class="close-btn" @click="close">×</button>
      </div>
      <div class="panel-body">
        <ChatContent embedded />
      </div>
    </div>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import ChatContent from './ChatContent.vue'

const props = defineProps<{
  systemId?: string
  apiUrl?: string
  position?: 'bottom-right' | 'bottom-left' | 'top-right' | 'top-left'
}>()

const isOpen = ref(false)

const position = props.position || 'bottom-right'

function open() {
  isOpen.value = true
}

function close() {
  isOpen.value = false
}
</script>

<style scoped>
.chat-widget {
  position: fixed;
  z-index: 9999;
}

.chat-widget.position-bottom-right {
  right: 20px;
  bottom: 20px;
}

.chat-widget.position-bottom-left {
  left: 20px;
  bottom: 20px;
}

.chat-widget.position-top-right {
  right: 20px;
  top: 20px;
}

.chat-widget.position-top-left {
  left: 20px;
  top: 20px;
}

.trigger-btn {
  width: 56px;
  height: 56px;
  border-radius: 50%;
  background: #4a90d9;
  color: #fff;
  border: none;
  cursor: pointer;
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.15);
  display: flex;
  align-items: center;
  justify-content: center;
  transition: transform 0.3s, box-shadow 0.3s;
}

.trigger-btn:hover {
  transform: scale(1.05);
  box-shadow: 0 6px 16px rgba(0, 0, 0, 0.2);
}

.trigger-btn .icon {
  font-size: 24px;
}

.chat-panel {
  width: 380px;
  height: 520px;
  background: #fff;
  border-radius: 12px;
  box-shadow: 0 8px 32px rgba(0, 0, 0, 0.15);
  display: flex;
  flex-direction: column;
  overflow: hidden;
}

.panel-header {
  padding: 12px 16px;
  background: #4a90d9;
  color: #fff;
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.panel-header .title {
  font-size: 16px;
  font-weight: 500;
}

.close-btn {
  background: transparent;
  border: none;
  color: #fff;
  font-size: 24px;
  cursor: pointer;
  line-height: 1;
}

.close-btn:hover {
  opacity: 0.8;
}

.panel-body {
  flex: 1;
  overflow: hidden;
}
</style>
