# AI Assistant Service - Quick Start Guide

## 快速开始

### 环境要求

- JDK 17+
- MySQL 8.0+
- Maven 3.8+
- Node.js 18+ (前端开发)

### 1. 数据库准备

```sql
CREATE DATABASE ai_assistant DEFAULT CHARSET utf8mb4;
```

### 2. 配置环境变量

```bash
export DB_URL=jdbc:mysql://localhost:3306/ai_assistant
export DB_USERNAME=root
export DB_PASSWORD=your_password
export ENCRYPTION_KEY=your-32-character-encryption-key-here!
```

### 3. 构建并运行后端

```bash
# 构建项目
mvn clean package -DskipTests

# 运行服务
java -jar target/ai-assistant-service-1.0.0-SNAPSHOT.jar
```

服务将运行在 `http://localhost:8081`

### 4. 配置 LLM API Key

```bash
# 配置 DeepSeek API Key
curl -X PUT http://localhost:8081/api/v1/config/api-key/deepseek \
  -H "Content-Type: application/json" \
  -d '{
    "providerType": "llm",
    "apiKey": "sk-your-deepseek-api-key"
  }'

# 配置 Serper API Key (可选，用于网络搜索)
curl -X PUT http://localhost:8081/api/v1/config/api-key/serper \
  -H "Content-Type: application/json" \
  -d '{
    "providerType": "search",
    "apiKey": "your-serper-api-key"
  }'
```

### 5. 注册目标系统

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

### 6. 运行前端 (开发模式)

```bash
cd frontend
npm install
npm run dev
```

前端将运行在 `http://localhost:5173`

### 7. 运行前端 (生产模式)

```bash
cd frontend
npm run build
# 使用 nginx 或其他静态服务器托管 dist 目录
```

---

## API 端点

### 健康检查

```
GET /health
GET /api/v1/health
```

### 上下文管理

```
POST /api/v1/context/push      # 推送用户上下文
GET /api/v1/context/{sessionId}/valid  # 验证会话
DELETE /api/v1/context/{sessionId}   # 清除上下文
```

### 系统管理

```
GET /api/v1/systems           # 系统列表
POST /api/v1/systems          # 注册系统
GET /api/v1/systems/{id}      # 系统详情
PUT /api/v1/systems/{id}      # 更新系统
DELETE /api/v1/systems/{id}   # 注销系统
```

### 对话接口

```
POST /api/v1/chat              # 流式对话 (SSE)
GET /api/v1/conversations      # 对话列表
GET /api/v1/conversations/{id} # 对话详情
DELETE /api/v1/conversations/{id} # 删除对话
```

### 配置管理

```
GET /api/v1/config/api-key           # API Key 配置列表
PUT /api/v1/config/api-key/{provider} # 设置 API Key
DELETE /api/v1/config/api-key/{provider} # 删除 API Key
```

### 技能管理

```
GET /api/v1/skills           # 技能列表
POST /api/v1/skills          # 创建技能
GET /api/v1/skills/{id}      # 技能详情
PUT /api/v1/skills/{id}      # 更新技能
DELETE /api/v1/skills/{id}   # 删除技能
```

---

## Docker 部署

```bash
# 使用 Docker Compose 启动完整服务栈
docker-compose up -d

# 服务访问:
# - 后端 API: http://localhost:8081
# - 前端: http://localhost:3000
# - MySQL: localhost:3306
```

---

## E2E 测试

```bash
cd e2e
npm install
npx playwright install

# 运行测试 (需要先启动后端服务)
npm run test
```

---

## 故障排查

### 常见错误

1. **SESSION_INVALID**: 会话无效或已过期
   - 解决: 重新推送用户上下文

2. **SYSTEM_NOT_FOUND**: 目标系统未注册
   - 解决: 调用 POST /api/v1/systems 注册系统

3. **LLM_ERROR**: LLM 调用失败
   - 解决: 检查 API Key 配置是否正确

4. **数据库连接失败**:
   - 检查 MySQL 是否运行
   - 检查环境变量 DB_URL, DB_USERNAME, DB_PASSWORD

---

## 项目状态

| 模块 | 状态 |
|------|------|
| 核心框架 | ✅ 完成 |
| 系统注册管理 | ✅ 完成 |
| 用户上下文管理 | ✅ 完成 |
| 对话引擎 | ✅ 完成 |
| MCP 工具路由 | ✅ 完成 |
| 技能系统 | ✅ 完成 |
| 配置管理 | ✅ 完成 |
| 网络搜索集成 | ✅ 完成 |
| 前端组件 | ✅ 完成 |
| E2E 测试 | ✅ 完成 |
| 浏览器自动化 | 🔄 占位实现 |
| OSRM 集成 | ⏳ 待完成 |

---

## 下一步

1. 集成到 OSRM 系统
2. 实现浏览器自动化 (Playwright)
3. 添加 WebSocket 支持 (可选)
4. 完善 API 文档 (Swagger)
