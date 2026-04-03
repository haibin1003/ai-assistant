# AI 小助手服务工作记录

## 2026-03-27 工作总结

### 当前状态

所有服务已成功启动：

| 服务 | 状态 | 端口 |
|------|------|------|
| OSRM 后端 | ✅ 运行中 | 8080 |
| OSRM 前端 | ✅ 运行中 | 5173 |
| MCP Gateway | ✅ 运行中 | 3000 |
| AI 助手后端 | ✅ 运行中 | 8081 |
| AI 助手前端 | ✅ 运行中 | 5174 |

---

### 已完成工作

#### 1. 服务修复与配置

1. **OSRM 后端配置修正**
   - 将 dev profile 从 H2 改为 MySQL 生产配置
   - 修改文件: `osrm-backend/src/main/resources/application-dev.yml`

2. **AI 助手服务修复**
   - 修复 MinIO 客户端 API 兼容性问题 (BucketExistsArgs/MakeBucketArgs)
   - 修复 MinIO 依赖版本 (8.5.9 → 8.5.14)
   - 修复 SkillPackageService 中的 IOException 处理
   - 移除 MinIO 依赖，改为本地文件系统存储
   - 配置文件更新为 MySQL 生产配置
   - 修改文件:
     - `ai-assistant-service/src/main/java/com/ai/assistant/common/config/MinioConfig.java`
     - `ai-assistant-service/src/main/java/com/ai/assistant/application/skill/SkillPackageService.java`
     - `ai-assistant-service/src/main/resources/application-dev.yml`
     - `ai-assistant-service/pom.xml`

#### 2. Skills 管理功能重构

1. **前端表单重构** (Settings.vue)
   - 移除 JSON 输入，改为交互式表单
   - 添加触发关键词标签输入
   - 添加所需工具标签输入
   - 添加文件上传区域 (scripts/references/assets)
   - 添加 SKILL.md 模板提示
   - 修改文件: `ai-assistant-service/frontend/src/views/Settings.vue`

2. **SkillPackageService 重构**
   - 从 MinIO 存储改为本地文件系统存储
   - 支持文件上传到 `skills-storage/` 目录
   - 修改文件: `ai-assistant-service/src/main/java/com/ai/assistant/application/skill/SkillPackageService.java`

#### 3. 文档编写

1. 创建系统设计文档
   - 文件: `ai-assistant-service/docs/design/ai-assistant-design.md`

---

### 功能清单

#### 已实现功能

| 功能 | 状态 | 说明 |
|------|------|------|
| 流式对话 (SSE) | ✅ | 支持 AI 回答流式输出 |
| 多模型支持 | ✅ | DeepSeek / OpenAI / Claude |
| 工具调用 | ✅ | AI 自动判断并调用 MCP 工具 |
| 用户上下文 | ✅ | 权限注入到 System Prompt |
| 系统注册 | ✅ | 注册 OSRM 等目标系统 |
| 工具发现 | ✅ | 获取系统 MCP 工具列表 |
| 简单 Skills | ✅ | 数据库存储的轻量级技能 |
| Skill Packages | ✅ | 文件系统存储的完整技能包 |
| 网络搜索 | ✅ | 集成 Serper API |
| 文档生成 | ✅ | Excel / Word 生成 |
| API Key 加密 | ✅ | 敏感信息加密存储 |
| 前端表单 | ✅ | 交互式 Skill 创建 |

#### 待完善功能

| 功能 | 优先级 | 说明 |
|------|--------|------|
| Skill 自动加载 | P1 | 发布后自动加载到对话 |
| 技能触发机制 | P2 | 关键词触发技能执行 |
| 浏览器自动化 | P2 | Playwright 集成 |
| MinIO 生产配置 | P2 | 正确配置生产环境存储 |

---

### 数据库配置

| 系统 | 主机 | 端口 | 数据库 | 用户名 | 密码 |
|------|------|------|--------|--------|------|
| OSRM | 114.66.38.81 | 3306 | osrm | osrm | osrm123 |
| AI 助手 | 114.66.38.81 | 3306 | ai_assistant | root | root123 |
| Redis | 114.66.38.81 | 16379 | - | - | - |

---

### 访问地址

- **OSRM 后端**: http://localhost:8080
- **OSRM 前端**: http://localhost:5173
- **MCP Gateway**: http://localhost:3000
  - 公开端点: http://localhost:3000/portal/mcp
  - 认证端点: http://localhost:3000/api/mcp
- **AI 助手后端**: http://localhost:8081
- **AI 助手前端**: http://localhost:5174
- **健康检查**: http://localhost:8081/health

---

### 测试账号

- **OSRM**: admin / admin123 (系统管理员)
- **AI 助手**: 登录后使用上下文推送

---

### 后续计划

1. **完善 Skill 触发机制** - SkillLoaderService 已在代码中实现，需要确保 Skill 发布后自动加载
2. **前端 UI 优化** - 继续美化前端界面
3. **E2E 测试** - 编写端到端测试
4. **MinIO 生产配置** - 正确配置生产环境对象存储

---

*记录时间: 2026-03-27*