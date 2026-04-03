# AI 助手服务总体设计文档

## 文档信息

- **项目名称**: AI 助手服务 (AI Assistant Service)
- **版本**: 1.0.0
- **创建日期**: 2026-03-25
- **文档类型**: 总体设计文档

---

## 1. 设计目标

### 1.1 核心设计原则

| 原则 | 说明 |
|------|------|
| **独立性** | 作为独立服务部署，不与任何特定业务系统耦合 |
| **可插拔** | 任何提供 MCP 服务的系统都可直接接入 |
| **多租户** | 支持多系统并发接入，会话数据完全隔离 |
| **弹性扩展** | 大模型 API、搜索服务可配置切换 |

### 1.2 系统边界

```
┌─────────────────────────────────────────────────────────────────────┐
│                      AI 助手服务系统边界                              │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│   ┌──────────────┐     ┌──────────────┐     ┌──────────────┐       │
│   │   前端组件   │     │   REST API   │     │  WebSocket   │       │
│   │ (Vue/iframe) │     │   (SSE)      │     │   (实时)     │       │
│   └──────┬───────┘     └──────┬───────┘     └──────┬───────┘       │
│          │                    │                    │                │
│          └────────────────────┼────────────────────┘                │
│                               ▼                                      │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │                    AI 助手服务核心                           │   │
│   │  ┌─────────┐ ┌─────────┐ ┌─────────┐ ┌─────────┐           │   │
│   │  │系统注册 │ │上下文   │ │对话引擎 │ │工具路由 │           │   │
│   │  │管理    │ │管理     │ │         │ │         │           │   │
│   │  └─────────┘ └─────────┘ └─────────┘ └─────────┘           │   │
│   │  ┌─────────┐ ┌─────────┐ ┌─────────┐                       │   │
│   │  │网络搜索 │ │技能系统 │ │配置管理 │                       │   │
│   │  │         │ │         │ │         │                       │   │
│   │  └─────────┘ └─────────┘ └─────────┘                       │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                               │                                      │
│   ┌───────────────────────────┼───────────────────────────────┐     │
│   │                           │                               │     │
│   │  ┌─────────┐    ┌─────────┴─────────┐    ┌─────────┐     │     │
│   │  │ MySQL   │    │ 外部依赖          │    │ 缓存    │     │     │
│   │  │ 数据库  │    │ • LLM API         │    │ (内存)  │     │     │
│   │  │         │    │ • 搜索 API        │    │         │     │     │
│   │  └─────────┘    │ • MCP 网关        │    └─────────┘     │     │
│   │                 └───────────────────┘                    │     │
│   └───────────────────────────────────────────────────────────┘     │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘

系统外：
┌─────────────┐  ┌─────────────┐  ┌─────────────┐
│ 目标系统 A   │  │ 目标系统 B   │  │ 目标系统 N   │
│ (MCP 网关)  │  │ (MCP 网关)  │  │ (MCP 网关)  │
└─────────────┘  └─────────────┘  └─────────────┘
```

---

## 2. 系统架构

### 2.1 分层架构

```
┌─────────────────────────────────────────────────────────────────────┐
│                          接口层 (Interfaces)                         │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐     │
│  │ REST Controller │  │ WebSocket       │  │ 健康检查        │     │
│  │ • ChatController│  │ Handler         │  │ • HealthCheck   │     │
│  │ • ContextCtrl   │  │ • ChatWSHandler │  │                 │     │
│  │ • SystemCtrl    │  │                 │  │                 │     │
│  │ • SkillCtrl     │  │                 │  │                 │     │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘     │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        应用服务层 (Application)                       │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐     │
│  │ ChatService     │  │ ContextService  │  │ SystemService   │     │
│  │ • 流式对话      │  │ • 上下文推送    │  │ • 系统注册      │     │
│  │ • 工具调用循环  │  │ • 会话管理      │  │ • 工具发现      │     │
│  │ • 上下文注入    │  │ • 权限缓存      │  │ • 健康检查      │     │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘     │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐     │
│  │ SkillService    │  │ SearchService   │  │ ConversationSvc │     │
│  │ • 技能管理      │  │ • 搜索 API      │  │ • 对话历史      │     │
│  │ • 技能触发      │  │ • 浏览器自动化  │  │ • 消息存储      │     │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘     │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                        领域层 (Domain)                               │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐     │
│  │ RegisteredSystem│  │ Session         │  │ Conversation    │     │
│  │ • systemId      │  │ • sessionId     │  │ • conversationId│     │
│  │ • mcpGatewayUrl │  │ • userContext   │  │ • messages      │     │
│  │ • toolPrefix    │  │ • accessToken   │  │                 │     │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘     │
│  ┌─────────────────┐  ┌─────────────────┐                          │
│  │ Message         │  │ Skill           │                          │
│  │ • role          │  │ • skillId       │                          │
│  │ • content       │  │ • promptTemplate│                          │
│  │ • toolCalls     │  │ • isGlobal      │                          │
│  └─────────────────┘  └─────────────────┘                          │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                      基础设施层 (Infrastructure)                      │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐     │
│  │ MCP Client      │  │ LLM Client      │  │ Search Client   │     │
│  │ • HTTP 调用     │  │ • DeepSeek      │  │ • Serper        │     │
│  │ • 工具发现      │  │ • OpenAI        │  │ • Tavily        │     │
│  │ • 工具调用      │  │ • Claude        │  │                 │     │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘     │
│  ┌─────────────────┐  ┌─────────────────┐                          │
│  │ Tool Router     │  │ Browser Tool    │                          │
│  │ • 前缀解析      │  │ • Playwright    │                          │
│  │ • 系统路由      │  │ • 页面快照      │                          │
│  └─────────────────┘  └─────────────────┘                          │
└─────────────────────────────────────────────────────────────────────┘
```

### 2.2 技术栈

| 层次 | 技术选型 | 说明 |
|------|----------|------|
| **框架** | Spring Boot 3.4 | Java 17，快速开发 |
| **数据库** | MySQL 8.0 | 关系型数据库 |
| **ORM** | Spring Data JPA | 领域驱动设计 |
| **数据库迁移** | Flyway | 版本化管理 |
| **流式响应** | Spring WebFlux | SSE 流式输出 |
| **WebSocket** | spring-websocket | 实时通信（可选） |
| **HTTP 客户端** | OkHttp | 调用外部 API |
| **JSON 处理** | Jackson | 序列化/反序列化 |
| **Lombok** | Lombok | 减少样板代码 |

---

## 3. 核心模块设计

### 3.1 模块依赖关系

```
┌─────────────────────────────────────────────────────────────────────┐
│                         interfaces (接口层)                          │
│     depends on → application                                        │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                       application (应用服务层)                       │
│     depends on → domain, infrastructure                            │
└─────────────────────────────────────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────┐
│                          domain (领域层)                             │
│     independent (不依赖其他层)                                        │
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│                     infrastructure (基础设施层)                      │
│     depends on → domain (实现接口)                                   │
└─────────────────────────────────────────────────────────────────────┘
```

### 3.2 模块职责

| 模块 | 包路径 | 职责 |
|------|--------|------|
| **系统注册管理** | `application.system` | 注册/更新/注销目标系统，工具发现与缓存 |
| **用户上下文管理** | `application.context` | 接收上下文推送，会话生命周期管理 |
| **对话引擎** | `application.chat` | 流式对话，工具调用循环，上下文注入 |
| **MCP 工具路由** | `infrastructure.mcp` | 工具名解析，系统路由，认证注入 |
| **网络搜索** | `infrastructure.search` | 搜索 API 调用，浏览器自动化 |
| **技能系统** | `application.skill` | 技能 CRUD，技能触发匹配 |
| **配置管理** | `application.config` | API Key 配置，系统配置 |

---

## 4. 数据流设计

### 4.1 对话请求流程

```
用户输入                    AI 助手服务                           目标系统
   │                           │                                    │
   │  POST /api/v1/chat        │                                    │
   │  X-Session-Id: xxx        │                                    │
   │  { "content": "..." }     │                                    │
   │──────────────────────────>│                                    │
   │                           │                                    │
   │                    1. 获取用户上下文                               │
   │                    context = getContext(sessionId)              │
   │                           │                                    │
   │                    2. 构建 System Prompt                         │
   │                    prompt = buildPrompt(context)                │
   │                           │                                    │
   │                    3. 获取可用工具列表                              │
   │                    tools = getAvailableTools(context)            │
   │                           │                                    │
   │                    4. 调用 LLM API (流式)                         │
   │                           │                                    │
   │  SSE: event: content      │                                    │
   │  data: {"content": "..."} │                                    │
   │<──────────────────────────│                                    │
   │                           │                                    │
   │                    5. AI 决定调用工具                              │
   │                           │                                    │
   │                    6. 工具路由                                    │
   │                    systemId = parseToolPrefix("osrm_search")     │
   │                           │                                    │
   │                           │  POST /api/mcp                      │
   │                           │  Authorization: Bearer {token}      │
   │                           │  { "method": "tools/call",          │
   │                           │    "params": {...} }                │
   │                           │───────────────────────────────────>│
   │                           │                                    │
   │                           │  { "result": {...} }                │
   │                           │<───────────────────────────────────│
   │                           │                                    │
   │  SSE: event: tool_result │                                    │
   │  data: {"success": true}  │                                    │
   │<──────────────────────────│                                    │
   │                           │                                    │
   │                    7. 继续对话直到完成                              │
   │                           │                                    │
   │  SSE: event: done         │                                    │
   │<──────────────────────────│                                    │
   │                           │                                    │
```

### 4.2 用户上下文推送流程

```
目标系统                    AI 助手服务
   │                           │
   │  POST /api/v1/context/push
   │  {                        │
   │    "sessionId": "uuid",   │
   │    "systemId": "osrm",    │
   │    "user": {...},         │
   │    "credentials": {...}   │
   │  }                        │
   │──────────────────────────>│
   │                           │
   │                    1. 验证系统注册状态
   │                    system = getSystem(systemId)
   │                           │
   │                    2. 创建/更新会话
   │                    session = saveOrUpdate(request)
   │                           │
   │                    3. 缓存用户上下文
   │                    contextCache.put(sessionId, context)
   │                           │
   │                    4. 返回会话信息
   │                           │
   │  { "code": 200,           │
   │    "data": {              │
   │      "sessionId": "...",  │
   │      "expiresAt": "..."   │
   │    }                      │
   │  }                        │
   │<──────────────────────────│
   │                           │
```

---

## 5. 接口设计

### 5.1 REST API 概览

| 模块 | 方法 | 路径 | 说明 |
|------|------|------|------|
| **对话** | POST | `/api/v1/chat` | 流式对话 (SSE) |
| | GET | `/api/v1/conversations` | 对话列表 |
| | GET | `/api/v1/conversations/{id}` | 对话详情 |
| | DELETE | `/api/v1/conversations/{id}` | 删除对话 |
| **上下文** | POST | `/api/v1/context/push` | 推送用户上下文 |
| | GET | `/api/v1/context/{sessionId}` | 获取用户上下文 |
| | DELETE | `/api/v1/context/{sessionId}` | 清除用户上下文 |
| **系统管理** | GET | `/api/v1/systems` | 已注册系统列表 |
| | POST | `/api/v1/systems` | 注册系统 |
| | GET | `/api/v1/systems/{systemId}` | 系统详情 |
| | PUT | `/api/v1/systems/{systemId}` | 更新系统 |
| | DELETE | `/api/v1/systems/{systemId}` | 注销系统 |
| | POST | `/api/v1/systems/{systemId}/refresh-tools` | 刷新工具缓存 |
| **技能管理** | GET | `/api/v1/skills` | 可用技能列表 |
| | POST | `/api/v1/skills` | 创建技能 |
| | PUT | `/api/v1/skills/{skillId}` | 更新技能 |
| | DELETE | `/api/v1/skills/{skillId}` | 删除技能 |
| **配置管理** | GET | `/api/v1/config/api-key` | 获取 API Key 配置状态 |
| | PUT | `/api/v1/config/api-key` | 配置 API Key |
| **健康检查** | GET | `/health` | 服务健康检查 |

### 5.2 SSE 事件类型

| 事件类型 | 数据格式 | 说明 |
|----------|----------|------|
| `content` | `{"content": "文本片段"}` | AI 输出内容 |
| `tool_use` | `{"tool": "工具名", "args": {...}}` | 工具调用开始 |
| `tool_result` | `{"success": true, "data": {...}}` | 工具调用结果 |
| `error` | `{"code": "错误码", "message": "错误信息"}` | 错误信息 |
| `done` | `{}` | 对话结束 |

---

## 6. 数据库设计

### 6.1 表结构概览

| 表名 | 说明 | 主要字段 |
|------|------|----------|
| `t_registered_system` | 注册的目标系统 | system_id, mcp_gateway_url, tool_prefix |
| `t_session` | 用户会话 | session_id, system_id, user_context, access_token |
| `t_conversation` | 对话 | conversation_id, session_id, title |
| `t_message` | 消息 | message_id, conversation_id, role, content, tool_calls |
| `t_skill` | 技能定义 | skill_id, prompt_template, is_global |
| `t_tool_cache` | 工具缓存 | system_id, tool_name, input_schema |
| `t_api_key_config` | API Key 配置 | provider, api_key(加密) |

### 6.2 ER 图

```
┌──────────────────┐       ┌──────────────────┐
│ t_registered_    │       │ t_session        │
│ system           │       │                  │
├──────────────────┤       ├──────────────────┤
│ PK id            │       │ PK id            │
│ UK system_id     │◄──────│ FK system_id     │
│ system_name      │       │ UK session_id    │
│ mcp_gateway_url  │       │ user_id          │
│ tool_prefix      │       │ user_context     │
│ auth_type        │       │ access_token     │
│ is_active        │       │ expires_at       │
└──────────────────┘       └────────┬─────────┘
                                    │
                                    │ 1:N
                                    ▼
                           ┌──────────────────┐
                           │ t_conversation   │
                           ├──────────────────┤
                           │ PK id            │
                           │ UK conversation_ │
                           │    id            │
                           │ FK session_id    │
                           │ title            │
                           │ message_count    │
                           └────────┬─────────┘
                                    │
                                    │ 1:N
                                    ▼
                           ┌──────────────────┐
                           │ t_message        │
                           ├──────────────────┤
                           │ PK id            │
                           │ UK message_id    │
                           │ FK conversation_ │
                           │    id            │
                           │ role             │
                           │ content          │
                           │ tool_calls (JSON)│
                           │ tool_results     │
                           └──────────────────┘

┌──────────────────┐       ┌──────────────────┐
│ t_tool_cache     │       │ t_skill          │
├──────────────────┤       ├──────────────────┤
│ PK id            │       │ PK id            │
│ FK system_id     │       │ UK skill_id      │
│ UK (system_id,   │       │ name             │
│     tool_name)   │       │ prompt_template  │
│ tool_name        │       │ required_tools   │
│ input_schema     │       │ is_global        │
│ fetched_at       │       │ created_by       │
└──────────────────┘       └──────────────────┘

┌──────────────────┐
│ t_api_key_config │
├──────────────────┤
│ PK id            │
│ UK provider      │
│ api_key (加密)    │
│ is_active        │
└──────────────────┘
```

---

## 7. 安全设计

### 7.1 认证与授权

| 层面 | 机制 | 说明 |
|------|------|------|
| **服务访问** | 无认证 | AI 助手服务本身不提供认证 |
| **会话验证** | Session ID | 通过 X-Session-Id 头标识用户 |
| **工具调用** | 目标系统认证 | 注入用户 Access Token 到 MCP 请求 |
| **管理接口** | 可选认证 | 系统注册等管理接口可配置认证 |

### 7.2 数据安全

| 数据类型 | 安全措施 |
|----------|----------|
| **API Key** | AES-256 加密存储 |
| **Access Token** | 加密存储，内存缓存时不持久化 |
| **用户上下文** | 会话隔离，过期自动清理 |
| **日志** | 敏感信息脱敏（Token、Key 不记录） |

### 7.3 安全边界

```
┌─────────────────────────────────────────────────────────────────────┐
│                          安全边界                                    │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│  外部不可信区域                                                      │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                                                              │   │
│  │   用户浏览器                                                 │   │
│  │                                                              │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│                              │ HTTPS                                │
│                              ▼                                       │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                                                              │   │
│  │   目标系统（可信，负责用户认证）                              │   │
│  │   • 推送已认证的用户上下文                                   │   │
│  │   • 提供 MCP 网关                                            │   │
│  │                                                              │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                              │                                       │
│                              │ 内网 HTTP                            │
│                              ▼                                       │
│  ┌─────────────────────────────────────────────────────────────┐   │
│  │                                                              │   │
│  │   AI 助手服务（可信）                                        │   │
│  │   • 信任目标系统推送的上下文                                 │   │
│  │   • 会话数据隔离存储                                         │   │
│  │   • 敏感数据加密                                             │   │
│  │                                                              │   │
│  └─────────────────────────────────────────────────────────────┘   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

---

## 8. 性能设计

### 8.1 缓存策略

| 缓存类型 | 数据 | 过期策略 | 存储 |
|----------|------|----------|------|
| **会话缓存** | UserContext | 会话过期 | 内存 (ConcurrentHashMap) |
| **工具缓存** | ToolDefinition | 5分钟 | 数据库 + 内存 |
| **系统缓存** | RegisteredSystem | 永久 | 数据库 |

### 8.2 性能指标

| 指标 | 目标值 | 实现策略 |
|------|--------|----------|
| **对话首字响应** | < 2s | 流式输出、并发请求 |
| **工具调用延迟** | < 5s | 异步调用、超时控制 |
| **并发用户数** | > 100 | 无状态设计、水平扩展 |
| **缓存命中率** | > 80% | 多级缓存、预加载 |

---

## 9. 部署架构

### 9.1 单机部署

```
┌─────────────────────────────────────────────────────────────────────┐
│                           服务器                                     │
├─────────────────────────────────────────────────────────────────────┤
│                                                                      │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │                    Nginx (反向代理)                          │   │
│   │                    :80 / :443                                │   │
│   └──────────────────────────┬──────────────────────────────────┘   │
│                              │                                       │
│                              ▼                                       │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │                    AI 助手服务                                │   │
│   │                    Spring Boot :8081                         │   │
│   └──────────────────────────┬──────────────────────────────────┘   │
│                              │                                       │
│                              ▼                                       │
│   ┌─────────────────────────────────────────────────────────────┐   │
│   │                    MySQL :3306                               │   │
│   └─────────────────────────────────────────────────────────────┘   │
│                                                                      │
└─────────────────────────────────────────────────────────────────────┘
```

### 9.2 集群部署

```
                    ┌─────────────┐
                    │  负载均衡器  │
                    │  (Nginx)    │
                    └──────┬──────┘
                           │
           ┌───────────────┼───────────────┐
           │               │               │
           ▼               ▼               ▼
    ┌─────────────┐ ┌─────────────┐ ┌─────────────┐
    │ AI 助手服务  │ │ AI 助手服务  │ │ AI 助手服务  │
    │ (Node 1)    │ │ (Node 2)    │ │ (Node N)    │
    └──────┬──────┘ └──────┬──────┘ └──────┬──────┘
           │               │               │
           └───────────────┼───────────────┘
                           │
                           ▼
                    ┌─────────────┐
                    │ MySQL 主从  │
                    │ 集群        │
                    └─────────────┘
```

---

## 10. 扩展性设计

### 10.1 LLM 提供商扩展

```java
// LLM 客户端接口
public interface LLMClient {
    Flux<ChatChunk> streamChat(String systemPrompt, List<Message> messages, List<Tool> tools);
}

// 实现类
public class DeepSeekClient implements LLMClient { ... }
public class OpenAIClient implements LLMClient { ... }
public class ClaudeClient implements LLMClient { ... }

// 工厂方法
public LLMClient createClient(String provider) {
    return switch (provider) {
        case "deepseek" -> new DeepSeekClient(apiKey);
        case "openai" -> new OpenAIClient(apiKey);
        case "claude" -> new ClaudeClient(apiKey);
        default -> throw new IllegalArgumentException("Unknown provider: " + provider);
    };
}
```

### 10.2 搜索提供商扩展

```java
// 搜索提供商接口
public interface SearchProvider {
    SearchResult search(String query, int limit);
}

// 实现类
public class SerperClient implements SearchProvider { ... }
public class TavilyClient implements SearchProvider { ... }
```

### 10.3 认证方式扩展

```java
// MCP 认证接口
public interface MCPAuthProvider {
    void applyAuth(HttpRequest request, UserContext context);
}

// 实现类
public class BearerAuthProvider implements MCPAuthProvider { ... }
public class BasicAuthProvider implements MCPAuthProvider { ... }
public class NoAuthProvider implements MCPAuthProvider { ... }
```

---

## 11. 监控与运维

### 11.1 健康检查

```json
GET /health

Response:
{
  "status": "UP",
  "components": {
    "database": "UP",
    "llmApi": "UP",
    "mcpGateways": {
      "osrm": "UP",
      "systemB": "DOWN"
    }
  }
}
```

### 11.2 日志规范

| 日志级别 | 使用场景 |
|----------|----------|
| ERROR | 系统错误、外部调用失败 |
| WARN | 业务异常、降级处理 |
| INFO | 关键业务节点、外部调用开始/结束 |
| DEBUG | 详细调试信息 |

### 11.3 关键指标监控

| 指标 | 说明 | 告警阈值 |
|------|------|----------|
| `chat.request.count` | 对话请求数 | - |
| `chat.response.time` | 响应时间 | P99 > 5s |
| `tool.call.count` | 工具调用数 | - |
| `tool.call.error` | 工具调用失败 | > 5% |
| `session.active.count` | 活跃会话数 | - |
| `llm.api.error` | LLM API 错误 | > 1% |

---

## 12. 开发里程碑

| 阶段 | 内容 | 预计工时 | 依赖 |
|------|------|----------|------|
| Phase 1 | 核心框架搭建 | 3 天 | - |
| Phase 2 | MCP 集成与工具路由 | 4 天 | Phase 1 |
| Phase 3 | 对话引擎 | 5 天 | Phase 2 |
| Phase 4 | 用户上下文推送 | 2 天 | Phase 1 |
| Phase 5 | 网络搜索集成 | 3 天 | Phase 3 |
| Phase 6 | 前端组件 | 5 天 | Phase 3 |
| Phase 7 | 系统管理 | 2 天 | Phase 1 |
| Phase 8 | 测试与文档 | 3 天 | All |
| **总计** | | **27 天** | |

---

## 附录

### A. 术语表

| 术语 | 说明 |
|------|------|
| MCP | Model Context Protocol，模型上下文协议 |
| 目标系统 | 被 AI 助手接入的业务系统 |
| 工具 | MCP 协议中定义的可调用功能 |
| 技能 | 预定义的操作序列模板 |
| 会话 | 用户在 AI 助手中的会话状态 |
| SSE | Server-Sent Events，服务器推送事件 |

### B. 参考文档

- [MCP 协议规范](https://modelcontextprotocol.io)
- [DeepSeek API 文档](https://platform.deepseek.com/docs)
- [OpenAI API 文档](https://platform.openai.com/docs)
- [Spring WebFlux 文档](https://docs.spring.io/spring-framework/reference/web/webflux.html)
