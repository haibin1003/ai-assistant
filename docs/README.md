# AI 助手服务文档索引

## 项目信息

- **项目名称**: AI 助手服务 (AI Assistant Service)
- **版本**: 1.0.0
- **创建日期**: 2026-03-25

---

## 文档结构

```
docs/
├── requirements/                    # 需求文档
│   └── ai-assistant-requirements.md # 需求规格说明书
│
├── design/                          # 设计文档
│   ├── overall-design.md            # 总体设计文档
│   ├── module-system-registration.md # 系统注册管理模块设计
│   ├── module-user-context.md       # 用户上下文管理模块设计
│   ├── module-chat-engine.md        # 对话引擎模块设计
│   ├── module-tool-router.md        # MCP工具路由模块设计
│   ├── module-skill-system.md       # 技能系统模块设计
│   └── module-config-management.md  # 配置管理模块设计
│
└── README.md                        # 本文件
```

---

## 阅读顺序

### Phase 1: 需求理解

1. [需求规格说明书](./requirements/ai-assistant-requirements.md)
   - 项目背景与目标
   - 用户角色定义
   - 功能需求详解
   - 非功能需求
   - 接口需求
   - 验收标准

### Phase 2: 架构设计

2. [总体设计文档](./design/overall-design.md)
   - 系统架构
   - 分层设计
   - 技术栈
   - 数据流设计
   - 安全设计
   - 部署架构

### Phase 3: 模块设计

3. [系统注册管理模块](./design/module-system-registration.md)
   - 系统注册/注销
   - 工具发现与缓存

4. [用户上下文管理模块](./design/module-user-context.md)
   - 上下文推送
   - 会话管理

5. [对话引擎模块](./design/module-chat-engine.md)
   - 流式对话
   - 多模型支持
   - 工具调用循环

6. [MCP工具路由模块](./design/module-tool-router.md)
   - 工具名解析
   - 工具执行
   - 内置工具

7. [技能系统模块](./design/module-skill-system.md)
   - 技能管理
   - 技能触发

8. [配置管理模块](./design/module-config-management.md)
   - API Key 配置
   - 加密存储

---

## 开发里程碑

| 阶段 | 内容 | 状态 |
|------|------|------|
| Phase 1 | 需求文档 | ✅ 完成 |
| Phase 2 | 总体设计 | ✅ 完成 |
| Phase 3 | 模块详细设计 | ✅ 完成 |
| Phase 4 | 后端开发 + 单元测试 | 🔄 待开始 |
| Phase 5 | 前端开发 | 🗒️ 未开始 |
| Phase 6 | 集成测试 | 🗒️ 未开始 |
| Phase 7 | 功能总结 | 🗒️ 未开始 |
| Phase 8 | OSRM 集成 | 🗒️ 未开始 |

---

## 技术栈

| 组件 | 技术方案 |
|------|----------|
| 后端框架 | Spring Boot 3.4 / Java 17 |
| 数据库 | MySQL 8.0 |
| ORM | Spring Data JPA |
| 数据库迁移 | Flyway |
| 流式响应 | Spring WebFlux |
| HTTP 客户端 | OkHttp |
| JSON 处理 | Jackson |

---

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.8+
- MySQL 8.0+

### 启动步骤

```bash
# 1. 创建数据库
mysql -u root -p -e "CREATE DATABASE ai_assistant DEFAULT CHARSET utf8mb4"

# 2. 配置环境变量
export DB_URL=jdbc:mysql://localhost:3306/ai_assistant
export DB_USERNAME=root
export DB_PASSWORD=password
export ENCRYPTION_KEY=your-encryption-key

# 3. 启动服务
mvn spring-boot:run
```

---

## API 概览

| 模块 | 端点 | 说明 |
|------|------|------|
| 对话 | `POST /api/v1/chat` | 流式对话 (SSE) |
| 上下文 | `POST /api/v1/context/push` | 推送用户上下文 |
| 系统管理 | `POST /api/v1/systems` | 注册系统 |
| 技能 | `POST /api/v1/skills` | 创建技能 |
| 配置 | `PUT /api/v1/config/api-key/{provider}` | 设置 API Key |

---

## 联系方式

如有问题，请联系项目负责人。
