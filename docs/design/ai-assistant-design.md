# AI 小助手服务设计文档

## 文档信息

- **项目名称**: AI 小助手 (OSRM AI Assistant)
- **版本**: 1.0.0
- **创建日期**: 2026-03-27
- **文档类型**: 系统设计文档

---

## 1. 系统概述

### 1.1 项目背景

AI 小助手是一个独立的、可插拔的 AI 助手服务，通过 MCP 协议接入 OSRM（开源软件仓库管理）系统。用户可以通过自然语言对话完成软件管理、订购申请等操作。

### 1.2 系统架构

```
┌─────────────────────────────────────────────────────────────┐
│                      前端 (Vue 3 + Vite)                    │
│  ┌─────────────┐  ┌─────────────┐  ┌─────────────────────┐ │
│  │ ChatWidget  │  │   Settings  │  │    Chat/Home         │ │
│  └─────────────┘  └─────────────┘  └─────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                    AI Assistant Service                      │
│                    (Spring Boot 3.4 + Java 17)              │
├─────────────────────────────────────────────────────────────┤
│  ┌───────────────┐  ┌───────────────┐  ┌───────────────┐   │
│  │  REST API     │  │  SSE 流式响应 │  │   WebSocket   │   │
│  └───────────────┘  └───────────────┘  └───────────────┘   │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐   │
│  │                    业务逻辑层                         │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌─────────┐│   │
│  │  │ ChatService│ │ContextSvc│ │SystemSvc │ │SkillSvc ││   │
│  │  └──────────┘ └──────────┘ └──────────┘ └─────────┘│   │
│  └─────────────────────────────────────────────────────┘   │
├─────────────────────────────────────────────────────────────┤
│  ┌─────────────────────────────────────────────────────┐   │
│  │                    基础设施层                         │   │
│  │  ┌──────────┐ ┌──────────┐ ┌──────────┐ ┌─────────┐│   │
│  │  │ LLM Client│ │MCP Client│ │ToolRouter│ │SearchCli││   │
│  │  └──────────┘ └──────────┘ └──────────┘ └─────────┘│   │
│  └─────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────┘
                              │
                              ▼
┌─────────────────────────────────────────────────────────────┐
│                      外部系统集成                            │
│  ┌─────────────────┐  ┌─────────────────┐  ┌────────────┐  │
│  │  OSRM MCP Gateway│  │  DeepSeek API   │  │ Serper API │  │
│  │   (Port 3000)    │  │  (大语言模型)   │  │ (网络搜索) │  │
│  └─────────────────┘  └─────────────────┘  └────────────┘  │
└─────────────────────────────────────────────────────────────┘
```

---

## 2. 核心功能模块

### 2.1 对话引擎 (ChatService)

**功能**:
- 流式对话响应 (SSE)
- 多模型支持 (DeepSeek / OpenAI / Claude)
- 工具调用 (Tool Calls)
- 上下文注入
- 对话历史管理

**关键类**:
- `ChatService` - 对话服务主类
- `ConversationService` - 对话管理
- `SystemPromptBuilder` - 系统提示词构建
- `LLMClient` / `DeepSeekClient` - LLM 客户端

### 2.2 用户上下文管理 (ContextService)

**功能**:
- 上下文推送 (目标系统推送用户上下文)
- 会话管理 (Session 生命周期)
- 权限注入 (用户角色权限注入)

**接口**:
```
POST /api/v1/context/push    - 推送用户上下文
GET  /api/v1/context/{sessionId}  - 获取上下文
DELETE /api/v1/context/{sessionId} - 清除上下文
```

### 2.3 系统注册管理 (SystemService)

**功能**:
- 注册目标系统
- 更新/注销系统
- 工具发现 (调用 MCP tools/list)
- 工具缓存

**接口**:
```
GET    /api/v1/systems              - 系统列表
POST   /api/v1/systems              - 注册系统
GET    /api/v1/systems/{systemId}  - 系统详情
PUT    /api/v1/systems/{systemId}  - 更新系统
DELETE /api/v1/systems/{systemId}  - 注销系统
POST   /api/v1/systems/{systemId}/refresh-tools - 刷新工具
```

### 2.4 Skills 管理

#### 2.4.1 简单 Skills (SkillService)

基于数据库的简单技能定义，适用于轻量级场景。

**接口**:
```
GET    /api/v1/skills           - 可用技能列表
POST   /api/v1/skills           - 创建技能
PUT    /api/v1/skills/{skillId} - 更新技能
DELETE /api/v1/skills/{skillId} - 删除技能
GET    /api/v1/skills/global    - 全局技能
GET    /api/v1/skills/my        - 我的技能
```

#### 2.4.2 Skill Packages (SkillPackageService)

基于文件系统的完整 Skill 包管理，支持 SKILL.md 格式。

**目录结构**:
```
skill-name/
├── SKILL.md           # 必需 - 技能定义
├── agents/            # 可选 - UI 元数据
├── scripts/           # 可选 - 可执行脚本
├── references/        # 可选 - 参考文档
└── assets/            # 可选 - 资源文件
```

**接口**:
```
GET    /api/v1/skill-packages          - 已发布 Skills
POST   /api/v1/skill-packages          - 创建并发布 Skill
GET    /api/v1/skill-packages/{skillId} - Skill 详情
DELETE /api/v1/skill-packages/{skillId} - 删除 Skill
POST   /api/v1/skill-packages/{skillId}/reload - 重载 Skill
```

**存储**:
- 开发环境: 本地文件系统 (`./skills-storage`)
- 生产环境: MinIO 对象存储 (可配置)

### 2.5 MCP 工具路由 (ToolRouter)

**功能**:
- 工具名解析 (解析前缀确定目标系统)
- 认证注入 (注入用户访问令牌)
- 工具调用 (调用目标系统 MCP)
- 结果处理

### 2.6 内置工具 (BuiltInToolService)

| 工具名 | 描述 |
|--------|------|
| web_search | 网络搜索 |
| browser_navigate | 打开指定网页 |
| browser_snapshot | 获取页面内容 |
| generate_excel | 生成 Excel 文档 |
| generate_word | 生成 Word 文档 |

### 2.7 网络搜索 (SearchClient / SerperClient)

集成 Serper/Tavily 搜索 API，提供网络搜索能力。

---

## 3. 数据库设计

### 3.1 表结构

| 表名 | 说明 |
|------|------|
| t_registered_system | 注册系统 |
| t_session | 用户会话 |
| t_conversation | 对话 |
| t_message | 消息 |
| t_skill | 简单技能 |
| t_skill_package | Skill 包 |
| t_api_key_config | API Key 配置 |
| t_tool_cache | 工具缓存 |
| t_generated_document | 生成的文档 |

### 3.2 数据库配置

- **主机**: 114.66.38.81
- **端口**: 3306
- **用户名**: root
- **密码**: root123
- **数据库名**: ai_assistant

---

## 4. API 接口

### 4.1 对话接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/chat | 流式对话 (SSE) |
| GET | /api/v1/conversations | 对话列表 |
| GET | /api/v1/conversations/{id} | 对话详情 |
| DELETE | /api/v1/conversations/{id} | 删除对话 |

### 4.2 上下文接口

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | /api/v1/context/push | 推送上下文 |
| GET | /api/v1/context/{sessionId} | 获取上下文 |
| DELETE | /api/v1/context/{sessionId} | 清除上下文 |

### 4.3 系统管理接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/v1/systems | 系统列表 |
| POST | /api/v1/systems | 注册系统 |
| GET | /api/v1/systems/{systemId} | 系统详情 |
| PUT | /api/v1/systems/{systemId} | 更新系统 |
| DELETE | /api/v1/systems/{systemId} | 注销系统 |

### 4.4 Skills 管理接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/v1/skills | 技能列表 |
| POST | /api/v1/skills | 创建技能 |
| PUT | /api/v1/skills/{skillId} | 更新技能 |
| DELETE | /api/v1/skills/{skillId} | 删除技能 |
| GET | /api/v1/skill-packages | 已发布 Skills |
| POST | /api/v1/skill-packages | 创建并发布 |

### 4.5 配置接口

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /api/v1/config/api-key | 获取 API Key 状态 |
| PUT | /api/v1/config/api-key | 配置 API Key |

### 4.6 健康检查

| 方法 | 路径 | 说明 |
|------|------|------|
| GET | /health | 服务健康检查 |

---

## 5. 配置文件

### 5.1 application.yml 关键配置

```yaml
server:
  port: 8081

spring:
  datasource:
    url: jdbc:mysql://114.66.38.81:3306/ai_assistant
    username: root
    password: root123

ai-assistant:
  llm:
    default-provider: deepseek
    deepseek:
      model: deepseek-chat
      api-url: https://api.deepseek.com/v1/chat/completions

  search:
    default-provider: serper
    serper:
      api-url: https://google.serper.dev/search

  minio:
    enabled: false  # 开发环境关闭
    endpoint: http://114.66.38.81:9000
    access-key: root
    secret-key: root12345

skill:
  storage:
    path: ./skills-storage
```

---

## 6. 部署

### 6.1 启动命令

```bash
# 启动后端服务
cd ai-assistant-service
mvn spring-boot:run -Dspring-boot.run.profiles=dev

# 启动前端开发服务器
cd frontend
npm run dev

# MCP Gateway (如需)
cd osrm-mcp
PORT=3000 OSRM_BASE_URL=http://localhost:8080/api/v1 node packages/gateway/dist/index.js
```

### 6.2 访问地址

- 后端 API: http://localhost:8081
- 前端页面: http://localhost:5174
- MCP Gateway: http://localhost:3000
- 健康检查: http://localhost:8081/health

---

## 7. 验收标准

### 7.1 功能验收

- [x] 用户可以通过自然语言与 AI 对话
- [x] AI 可以调用目标系统的 MCP 工具
- [x] 用户上下文正确注入到对话
- [x] 支持多个目标系统同时接入
- [x] Skills 管理系统完成 (表单式创建)
- [x] Skill 包支持文件上传

### 7.2 性能验收

- [ ] 对话首字响应时间 < 2 秒
- [ ] 工具调用延迟 < 5 秒

### 7.3 安全验收

- [x] API Key 加密存储
- [x] 访问令牌安全
- [x] 不同用户会话隔离

---

## 8. 后续工作

### 8.1 待完成

1. Skill 触发机制完善 - 自动加载已发布 Skills 到对话
2. 浏览器自动化工具完善
3. 技能模板市场

### 8.2 优化方向

1. MinIO 生产环境配置
2. 缓存优化
3. 监控告警

---

*文档版本: 1.0.0*
*最后更新: 2026-03-27*