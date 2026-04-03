# AI 助手管理系统 - 开发完成报告

**生成日期**: 2026-03-26
**项目路径**: `C:\Users\51554\claude\ai-assistant-service`

---

## 1. 项目概述

AI 助手管理系统是一个独立的智能服务，通过 MCP（Model Context Protocol）协议接入目标系统（如 OSRM）。用户可以通过 Web 界面与 AI 对话，AI 自动调用已接入系统的工具完成任务。

### 核心特性
- **独立性**：独立部署的服务，不与任何特定系统耦合
- **多系统接入**：支持同时接入多个目标系统
- **用户上下文**：支持登录和非登录（游客）两种模式
- **Skills 管理**：用户可创建、编辑、发布、删除自定义 Skills

---

## 2. 架构设计

### 2.1 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                    AI 助手服务                               │
├─────────────────────────────────────────────────────────────┤
│  前端 (Vue 3)                                               │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐         │
│  │ /chat       │  │ /settings   │  │ /embed      │         │
│  │ 对话页面    │  │ 配置页面    │  │ 嵌入页面    │         │
│  └──────┬──────┘  └──────┬──────┘  └──────────┬────────┘  │
└─────────┼────────────────┼────────────────────┼────────────┘
          │                │                    │
          ▼                ▼                    ▼
┌─────────────────────────────────────────────────────────────┐
│  后端 (Spring Boot 3.4 / Java 17)                           │
│  ┌──────────────────────────────────────────────────────┐   │
│  │ 对话服务 (ChatService)                                │   │
│  │ - 流式响应 (SSE)                                      │   │
│  │ - 工具调用循环                                        │   │
│  │ - 游客模式支持                                        │   │
│  └──────────────────────────────────────────────────────┘   │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐       │
│  │ 上下文服务    │ │ Skills 服务   │ │ 系统服务      │       │
│  │ ContextService│ │ SkillService │ │ SystemService │       │
│  └──────────────┘ └──────────────┘ └──────────────┘       │
└─────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────┐
│  基础设施层                                                   │
│  ┌──────────────┐ ┌──────────────┐ ┌──────────────┐       │
│  │ LLM 客户端    │ │ MCP 客户端    │ │ 搜索服务      │       │
│  │ DeepSeek     │ │ ToolRouter    │ │ Serper       │       │
│  └──────────────┘ └──────────────┘ └──────────────┘       │
└─────────────────────────────────────────────────────────────┘
          │
          ▼
┌─────────────────────────────────────────────────────────────┐
│  目标系统 (MCP Server)                                       │
│  ┌──────────────────────┐  ┌──────────────────────┐      │
│  │ OSRM MCP Gateway     │  │ 其他系统 MCP          │      │
│  │ /portal/mcp (公开)   │  │ /api/mcp (认证)       │      │
│  └──────────────────────┘  └──────────────────────┘      │
└─────────────────────────────────────────────────────────────┘
```

### 2.2 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| AI 助手前端 | 3002 | Web 界面 |
| AI 助手后端 | 8081 | REST API + SSE |
| MCP 网关 | 3001 | MCP 协议接入 |
| OSRM 后端 | 8080 | OSRM 服务 |

---

## 3. 功能实现

### 3.1 用户角色与权限

| 角色 | 说明 | 权限 |
|------|------|------|
| **游客** | 未登录用户 | 只能使用公开的 MCP 工具（无认证）+ 内置工具 |
| **用户** | 已登录用户 | 可使用所有已接入系统的 MCP 工具 |
| **管理员** | 管理员用户 | 可配置模型、接入系统、管理用户（待扩展） |

### 3.2 页面结构

```
/                   → Home.vue (首页重定向到 /chat)
/chat               → Chat.vue (对话页面)
/chat/:id           → Chat.vue (指定对话)
/settings           → Settings.vue (配置页面)
/embed              → Embed.vue (嵌入模式)
```

### 3.3 对话页面功能

- **顶部导航**：AI 助手标题 + 对话/配置菜单
- **左侧边栏**：已接入系统列表
- **消息布局**：用户消息在右侧，AI 消息在左侧
- **登录/退出**：支持登录 OSRM 系统，退出后进入游客模式
- **游客模式**：点击"不登录使用"进入，只能调用公开工具

### 3.4 配置页面功能

- **Skills 管理 Tab**
  - 全局 Skills（所有人可用）
  - 我的 Skills（自己创建的）
  - 创建新 Skill
  - 编辑/删除/发布 Skill

- **系统接入 Tab**
  - 查看已接入系统
  - 查看系统工具列表

---

## 4. 核心交互流程

### 4.1 用户登录流程

```
1. 用户点击"登录系统"
2. 弹出登录对话框，选择系统，输入用户名/密码
3. 调用 POST /api/v1/context/push 推送上下文
4. 后端保存会话和凭证
5. 前端跳转到已登录状态，可使用全部工具
```

### 4.2 游客使用流程

```
1. 用户点击"不登录使用"
2. 系统设为游客模式
3. 用户发送消息
4. 后端检测无会话，创建游客上下文
5. 游客只能调用内置工具（web_search, browser_*）和公开 MCP 工具
```

### 4.3 权限控制逻辑（后端）

```java
// ChatService.java - getAvailableTools()
boolean isLoggedIn = context.getUserId() != null && !"guest".equals(context.getUserId());

if (isLoggedIn && context.getSystemId() != null) {
    // 登录用户：获取系统的所有工具
    tools.addAll(systemService.getSystemTools(...));
}

// 内置工具（web_search, browser_*）对所有用户开放
tools.addAll(getBuiltInTools());
```

---

## 5. API 设计

### 5.1 对话 API

```http
POST /api/v1/chat
Content-Type: application/json
X-Session-Id: {sessionId}

{ "content": "你好", "conversationId": "可选" }

# 响应 (SSE)
data: {"event":"content","data":{"content":"你好！"}}
data: {"event":"tool_use","data":{"tool":"osrm_search","args":{...}}}
data: {"event":"tool_result","data":{"success":true,"data":"..."}}
data: {"event":"done","data":{}}
```

### 5.2 上下文推送 API

```http
POST /api/v1/context/push

{
  "sessionId": "xxx",
  "systemId": "osrm",
  "user": { "id": 1, "username": "admin", "roles": ["USER"] },
  "credentials": { "username": "admin", "password": "xxx" }
}
```

### 5.3 Skills API

```http
GET  /api/v1/skills/global    # 获取全局技能
GET  /api/v1/skills/my        # 获取我的技能
POST /api/v1/skills           # 创建技能
PUT  /api/v1/skills/{id}      # 更新技能
DELETE /api/v1/skills/{id}    # 删除技能
```

---

## 6. 测试结果

### 6.1 E2E 测试

| 测试项 | 结果 | 说明 |
|--------|------|------|
| 主聊天流程 | ✅ 通过 | 发送消息，获取 AI 响应 |
| 完整流程（OSRM） | ✅ 通过 | 登录上下文推送，11条消息交互 |
| 游客模式 | ✅ 通过 | 游客可以对话和获取响应 |

### 6.2 后端健康检查

```bash
curl http://localhost:8081/actuator/health
# {"status":"UP"}
```

---

## 7. 文件清单

### 7.1 后端核心文件

| 文件 | 说明 |
|------|------|
| `src/main/java/.../AiAssistantApplication.java` | 启动类 |
| `src/main/java/.../application/chat/ChatService.java` | 对话服务（含游客模式） |
| `src/main/java/.../application/context/ContextService.java` | 上下文服务 |
| `src/main/java/.../application/skill/SkillService.java` | Skills 服务 |
| `src/main/java/.../application/system/SystemService.java` | 系统服务 |
| `src/main/java/.../infrastructure/mcp/ToolRouter.java` | MCP 工具路由 |
| `src/main/java/.../infrastructure/llm/DeepSeekClient.java` | DeepSeek LLM 客户端 |

### 7.2 前端核心文件

| 文件 | 说明 |
|------|------|
| `frontend/src/views/Chat.vue` | 对话页面（新布局） |
| `frontend/src/views/Settings.vue` | 配置页面（Skills 管理） |
| `frontend/src/views/Embed.vue` | 嵌入页面 |
| `frontend/src/components/ChatContent.vue` | 聊天内容组件 |
| `frontend/src/stores/assistant.ts` | 状态管理 |
| `frontend/src/router/index.ts` | 路由配置 |
| `frontend/src/api/index.ts` | API 封装 |

### 7.3 设计文档

| 文件 | 说明 |
|------|------|
| `docs/design/ai-assistant-management.md` | 完整设计文档 |

---

## 8. 验收标准检查

| 功能 | 状态 | 说明 |
|------|------|------|
| 机器人独立性 | ✅ | 独立服务，独立部署 |
| 管理端 | ✅ | 配置页面可管理 Skills 和系统 |
| 游客模式 | ✅ | 不登录只能使用公开工具 |
| 登录模式 | ✅ | 登录可使用全部工具 |
| 多菜单 | ✅ | /chat 和 /settings 两个页面 |
| 对话布局 | ✅ | 用户消息在右，AI 在左 |
| Skills 管理 | ✅ | 创建/编辑/发布/删除 |
| 系统接入查看 | ✅ | 查看已接入系统列表 |

---

## 9. 启动命令

```bash
# 1. 启动 OSRM 后端 (端口 8080)
cd C:\Users\51554\claude\osrm-backend
mvn spring-boot:run

# 2. 启动 MCP 网关 (端口 3001)
cd C:\Users\51554\claude\osrm-mcp
PORT=3000 OSRM_BASE_URL=http://localhost:8080/api/v1 node packages/gateway/dist/index.js

# 3. 启动 AI 助手后端 (端口 8081)
cd C:\Users\51554\claude\ai-assistant-service
mvn spring-boot:run

# 4. 启动 AI 助手前端 (端口 3002)
cd C:\Users\51554\claude\ai-assistant-service\frontend
npm run dev -- --port 3002
```

---

## 10. 待完成项

1. **管理员功能**：模型配置、系统接入管理（需扩展）
2. **会话持久化**：当前会话存储在内存，可考虑持久化到数据库
3. **错误处理完善**：更多边界情况处理
4. **性能优化**：LLM 响应缓存、工具结果缓存

---

## 11. 总结

AI 助手管理系统已完成核心功能开发，包括：

1. **设计文档** - 完整的系统架构、交互流程、API 设计
2. **后端服务** - 游客模式、权限控制、流式对话、Skills 管理
3. **前端界面** - 对话页面（用户右AI左）、配置页面（Skills 管理）
4. **测试验证** - E2E 测试通过，核心流程可用

系统可独立部署，通过 MCP 协议接入任意目标系统。用户可选择登录或游客模式，登录后可使用目标系统的全部工具，游客仅可使用公开工具和内置工具（搜索、浏览器）。
