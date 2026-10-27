# OfferPilot

面向 Java 后端 / Java+AI 求职者的垂直 AI 模拟面试教练。

当前进度：P0 Week 2 已完成（简历上传解析、创建面试、面试蓝图、前端创建弹窗与预览页）。

下一步：P0 Week 3（多轮对话、SSE、追问/提示/跳过、状态机）。

## 目录

- `backend/`：Spring Boot 3.5 + MyBatis-Plus + Flyway
- `frontend/`：Vue3 + Vite + TypeScript + Element Plus
- `docs/`：实施计划
- `OfferPilot-智能AI面试问答项目总体方案-V1.0.docx`：产品与技术方案

## 快速开始

### 后端（H2，默认，不需要 Docker）

```bash
cd backend
mvn spring-boot:run
```

- API: http://localhost:8080
- Swagger UI: http://localhost:8080/swagger-ui.html
- H2 Console: http://localhost:8080/h2-console
  - JDBC URL: `jdbc:h2:mem:offerpilot`
  - User: `sa`，Password 留空

### 后端（MySQL）

先启动 Docker Desktop，然后：

```bash
docker compose up -d mysql
cd backend
SPRING_PROFILES_ACTIVE=mysql mvn spring-boot:run
```

PowerShell:
```powershell
$env:SPRING_PROFILES_ACTIVE="mysql"; mvn spring-boot:run
```

### 前端

```bash
cd frontend
npm install
npm run dev
```

访问 http://localhost:5173。

### 测试

```bash
cd backend
mvn test
```

## 当前 API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/register` | 注册并返回令牌 |
| POST | `/api/auth/login` | 登录 |
| POST | `/api/auth/refresh` | 刷新令牌（轮换） |
| POST | `/api/auth/logout` | 注销 refresh token |
| GET | `/api/auth/me` | 当前用户 |
| GET | `/api/job-templates` | 岗位模板列表 |
| POST | `/api/resumes` | 上传 PDF/DOCX 简历并解析 |
| GET | `/api/resumes` | 当前用户简历列表 |
| GET | `/api/resumes/{id}` | 简历详情 |
| DELETE | `/api/resumes/{id}` | 删除简历 |
| POST | `/api/interviews` | 创建面试并生成蓝图 |
| GET | `/api/interviews/{id}` | 面试详情与题目 |
| GET | `/api/interviews` | 面试历史 |

## AI 配置（可选）

默认 `app.ai.enabled=false`，使用确定性的 Mock 蓝图生成器，不需要 DeepSeek Key。

启用真实 AI：

```bash
export DEEPSEEK_API_KEY=sk-...
export APP_AI_ENABLED=true
cd backend && mvn spring-boot:run
```

PowerShell:
```powershell
$env:DEEPSEEK_API_KEY="sk-..."
$env:APP_AI_ENABLED="true"
mvn spring-boot:run
```

AI 调用失败会自动回退 Mock 生成器，不会阻塞面试创建。上传的简历默认保存在 `backend/data/uploads`，可通过 `OFFERPILOT_UPLOAD_DIR` 修改。

## 开发约定

- Java 21，包名 `com.offerpilot`。
- 统一响应：`{ code, message, data, traceId }`，`code = 0` 表示成功。
- 时间字段使用 `LocalDateTime`，数据库用 `DATETIME(3)`。
- 所有表使用逻辑删除 `deleted`（0/1）。
- 每个任务完成后提交一次 git commit。
