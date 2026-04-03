<template>
  <div class="ai-assistant-app">
    <router-view />
  </div>
</template>

<script setup lang="ts">
import { onMounted } from 'vue'
import { useAssistantStore } from './stores/assistant'

const store = useAssistantStore()

onMounted(() => {
  // 初始化配置
  store.initConfig()
})
</script>

<style scoped>
.ai-assistant-app {
  width: 100%;
  height: 100%;
}
</style>
