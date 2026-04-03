<template>
  <div class="home-container">
    <div class="hero">
      <h1>AI 助手服务</h1>
      <p>独立的、可插拔的 AI 助手，通过 MCP 协议接入任何目标系统</p>
    </div>

    <div class="features">
      <div class="feature-card">
        <div class="feature-icon">🔌</div>
        <h3>可插拔接入</h3>
        <p>任何提供 MCP 服务的系统都可以直接接入</p>
      </div>
      <div class="feature-card">
        <div class="feature-icon">🤖</div>
        <h3>智能对话</h3>
        <p>自然语言交互，自动调用工具完成任务</p>
      </div>
      <div class="feature-card">
        <div class="feature-icon">🔐</div>
        <h3>安全可靠</h3>
        <p>会话隔离，敏感数据加密存储</p>
      </div>
    </div>

    <div class="actions">
      <button class="btn-primary" @click="goToChat">
        开始对话
      </button>
    </div>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'

const router = useRouter()

function goToChat() {
  router.push('/chat')
}
</script>

<style scoped>
.home-container {
  max-width: 960px;
  margin: 0 auto;
  padding: 60px 20px;
  text-align: center;
}

.hero h1 {
  font-size: 42px;
  color: #333;
  margin-bottom: 16px;
}

.hero p {
  font-size: 18px;
  color: #666;
  margin-bottom: 48px;
}

.features {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 24px;
  margin-bottom: 48px;
}

.feature-card {
  background: #fff;
  padding: 32px;
  border-radius: 12px;
  box-shadow: 0 2px 8px rgba(0, 0, 0, 0.1);
}

.feature-icon {
  font-size: 48px;
  margin-bottom: 16px;
}

.feature-card h3 {
  font-size: 18px;
  color: #333;
  margin-bottom: 8px;
}

.feature-card p {
  font-size: 14px;
  color: #666;
}

.btn-primary {
  padding: 16px 48px;
  background: #4a90d9;
  color: #fff;
  border: none;
  border-radius: 8px;
  font-size: 16px;
  cursor: pointer;
}

.btn-primary:hover {
  background: #3a7bc8;
}

@media (max-width: 768px) {
  .features {
    grid-template-columns: 1fr;
  }
}
</style>
