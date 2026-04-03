# AI 助手服务集成指南

本文档说明如何将 AI 助手服务集成到目标系统。

---

## 1. 部署 AI 助手服务

### 1.1 环境要求

- JDK 17+
- MySQL 8.0+
- Maven 3.8+

### 1.2 数据库准备

```sql
CREATE DATABASE ai_assistant DEFAULT CHARSET utf8mb4;
```

### 1.3 配置环境变量

```bash
export DB_URL=jdbc:mysql://localhost:3306/ai_assistant
export DB_USERNAME=root
export DB_PASSWORD=your_password
export ENCRYPTION_KEY=your-32-character-encryption-key
```

### 1.4 启动服务

```bash
# 构建项目
mvn clean package -DskipTests

# 运行服务
java -jar target/ai-assistant-service-1.0.0.jar
```

服务默认运行在 `http://localhost:8081`

---

## 2. 注册目标系统

### 2.1 注册系统

```bash
curl -X POST http://localhost:8081/api/v1/systems \
  -H "Content-Type: application/json" \
  -d '{
    "systemId": "osrm",
    "systemName": "开源软件仓库管理",
    "mcpGatewayUrl": "http://localhost:3000/api/mcp",
    "authType": "bearer",
    "toolPrefix": "osrm_",
    "description": "OSRM 软件管理系统"
  }'
```

### 2.2 验证注册

```bash
curl http://localhost:8081/api/v1/systems/osrm
```

---

## 3. 配置 API Key

### 3.1 配置 LLM API Key

```bash
curl -X PUT http://localhost:8081/api/v1/config/api-key/deepseek \
  -H "Content-Type: application/json" \
  -d '{
    "providerType": "llm",
    "apiKey": "sk-your-deepseek-api-key"
  }'
```

### 3.2 配置搜索 API Key (可选)

```bash
curl -X PUT http://localhost:8081/api/v1/config/api-key/serper \
  -H "Content-Type: application/json" \
  -d '{
    "providerType": "search",
    "apiKey": "your-serper-api-key"
  }'
```

---

## 4. 前端集成

### 方式一：Vue 组件嵌入

```vue
<template>
  <div class="app-container">
    <!-- 你的应用内容 -->

    <!-- AI 助手组件 -->
    <ChatWidget
      api-url="http://localhost:8081"
      system-id="osrm"
      position="bottom-right"
    />
  </div>
</template>

<script setup>
import { ChatWidget } from '@ai-assistant/chat-component'
</script>
```

### 方式二：iframe 嵌入

```html
<iframe
  src="http://localhost:8081/embed?system=osrm"
  style="width: 400px; height: 600px; border: none;"
/>
```

### 方式三：独立部署

直接访问 `http://localhost:3000` 使用独立门户。

---

## 5. 用户上下文推送

用户登录目标系统后，需要推送用户上下文到 AI 助手。

### 5.1 推送时机

- 用户登录成功后
- 用户权限变更后

### 5.2 推送示例

```javascript
// 用户登录后推送上下文
async function pushUserContext(user, accessToken) {
  const response = await fetch('http://localhost:8081/api/v1/context/push', {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json'
    },
    body: JSON.stringify({
      sessionId: generateSessionId(), // 前端生成并存储
      systemId: 'osrm',
      user: {
        id: user.id,
        username: user.username,
        roles: user.roles,
        permissions: user.permissions,
        realName: user.realName,
        email: user.email
      },
      credentials: {
        accessToken: accessToken,
        expiresIn: 7200
      }
    })
  });

  const data = await response.json();
  return data.data.sessionId; // 存储 sessionId 用于后续请求
}
```

### 5.3 iframe 模式推送

```javascript
// 通过 postMessage 推送上下文到 iframe
const iframe = document.getElementById('ai-assistant-iframe');

iframe.contentWindow.postMessage({
  type: 'USER_CONTEXT',
  payload: {
    sessionId: 'xxx',
    systemId: 'osrm',
    accessToken: 'xxx'
  }
}, '*');
```

---

## 6. 发起对话

### 6.1 REST API 方式

```javascript
const response = await fetch('http://localhost:8081/api/v1/chat', {
  method: 'POST',
  headers: {
    'Content-Type': 'application/json',
    'X-Session-Id': sessionId
  },
  body: JSON.stringify({
    content: '帮我上传一个 Nginx 软件'
  })
});

// 处理 SSE 流式响应
const reader = response.body.getReader();
const decoder = new TextDecoder();

while (true) {
  const { done, value } = await reader.read();
  if (done) break;

  const chunk = decoder.decode(value);
  // 解析 SSE 事件
  console.log(chunk);
}
```

### 6.2 使用前端组件

前端组件已封装好 SSE 处理逻辑，直接使用即可：

```vue
<ChatWidget system-id="osrm" />
```

---

## 7. 用户登出处理

用户登出目标系统时，需要清除 AI 助手会话：

```javascript
async function clearUserContext(sessionId) {
  await fetch(`http://localhost:8081/api/v1/context/${sessionId}`, {
    method: 'DELETE'
  });
}
```

---

## 8. 完整集成流程

```
┌─────────────────────────────────────────────────────────────────────┐
│                        目标系统 (OSRM)                               │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│   1. 用户登录 OSRM                                                   │
│      ↓                                                              │
│   2. OSRM 调用 POST /api/v1/context/push                            │
│      推送用户上下文 + AccessToken                                    │
│      ↓                                                              │
│   3. OSRM 前端存储 sessionId                                        │
│      ↓                                                              │
│   4. 用户使用 AI 助手组件                                            │
│      携带 X-Session-Id 头                                           │
│      ↓                                                              │
│   5. AI 助手调用 OSRM MCP 工具                                       │
│      注入用户 AccessToken                                            │
│      ↓                                                              │
│   6. OSRM MCP 网关验证权限                                           │
│      执行工具并返回结果                                              │
│      ↓                                                              │
│   7. AI 助手返回结果给用户                                           │
│                                                                      │
│   8. 用户登出 OSRM                                                   │
│      ↓                                                              │
│   9. OSRM 调用 DELETE /api/v1/context/{sessionId}                   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 9. 错误处理

### 常见错误码

| 错误码 | 说明 | 处理方式 |
|--------|------|----------|
| SESSION_INVALID | 会话无效或已过期 | 重新推送上下文 |
| SYSTEM_NOT_FOUND | 目标系统未注册 | 联系管理员注册系统 |
| TOOL_NOT_FOUND | 工具不存在 | 检查工具名前缀 |
| LLM_ERROR | LLM 调用失败 | 检查 API Key 配置 |

---

## 10. 安全建议

1. **HTTPS**: 生产环境必须使用 HTTPS
2. **Token 有效期**: 设置合理的 Token 有效期
3. **访问控制**: 限制 AI 助手服务的访问来源
4. **日志审计**: 记录关键操作日志
5. **密钥管理**: 使用专业的密钥管理服务存储加密密钥
