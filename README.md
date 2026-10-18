# SpecTrace · 手机参数对比平台

[![Build & Publish Docker Images](https://github.com/jingyuan9527/SpecTrace/actions/workflows/docker-publish.yml/badge.svg)](https://github.com/jingyuan9527/SpecTrace/actions/workflows/docker-publish.yml)

面向数码爱好者的手机参数数据库：收录多品牌机型参数，支持**多机型横向对比**、**单参数跨代历史演变**、**对比结果一键截图分享**；数据来源为管理员手动录入 + AgentScope AI Agent 自动抓取（抓取结果必须人工审核后入库）。

> 仓库地址：<https://github.com/jingyuan9527/SpecTrace>
> 容器镜像：`ghcr.io/jingyuan9527/spectrace-{backend,frontend,agent}`

## 架构

```
┌──────────────┐   HTTP    ┌─────────────────────────┐
│  Vue3 前端   │ ────────► │  SpringBoot 3.x 业务后端 │ ──► MySQL（主业务库）
│ 访客端/后台  │ ◄──────── │  MyBatis-Plus / JWT/RBAC │ ──► Redis（缓存）
└──────────────┘           └───────────┬─────────────┘
                                       │ Redis List（任务队列）
                                       │ HTTP 回调（结果回传）
                           ┌───────────▼─────────────┐
                           │ AgentScope 采集 Agent    │
                           │ 搜索→抓取→LLM抽取→JSON   │
                           └─────────────────────────┘
```

**职责隔离**：AgentScope 不持有任何数据库连接，只负责采集与回调；所有入库、审核状态流转均由 SpringBoot 掌管。
**数据存储解耦**：MySQL / Redis 支持**外部托管**（默认）或由 Docker 内置，二选一，见下文部署说明。

## 目录结构

```
SpecTrace/
├── backend/         SpringBoot 3.3 + MyBatis-Plus 业务后端（默认 H2 内存库启动）+ Dockerfile
├── frontend/        Vue3 + Element Plus + TailwindCSS + ECharts + html2canvas + Dockerfile/nginx.conf
├── agent/           AgentScope Java 2.0 采集 Agent（Harness 模式 · Spring Boot / Maven）+ Dockerfile
├── sql/             MySQL 生产库建表脚本
├── docs/            SpringBoot ↔ AgentScope 交互接口（OpenAPI）
├── .github/workflows/docker-publish.yml   构建并推送镜像到 GHCR
├── docker-compose.yml       应用编排（默认拉取 GHCR 镜像，连接外部 MySQL/Redis）
├── docker-compose.build.yml 源码构建覆盖文件
├── docker-compose.infra.yml 内置 MySQL/Redis 叠加文件
└── .env.example             部署环境变量样例
```

## 快速启动（本地开发）

### 1. 后端（无需安装 MySQL / Redis）

```bash
cd backend
mvn spring-boot:run
```

- 默认 `dev` profile，使用 H2 内存库并自动建表、写入种子数据
- 端口 `8080`，接口文档：http://localhost:8080/swagger-ui.html
- H2 控制台：http://localhost:8080/h2-console （JDBC URL `jdbc:h2:mem:spectrace`，用户 `sa`，空密码）
- 初始超级管理员：`admin / admin123`（启动时以 BCrypt 写入）

### 2. 前端

```bash
cd frontend
npm install
npm run dev
```

- 访问 http://localhost:5173 ，已配置 `/api`、`/uploads` 代理到后端 8080
- 访客端：`/#/` 首页、`/#/devices` 机型库、`/#/compare` 对比、`/#/history` 参数历史
- 管理后台：`/#/admin/login`

### 3. AgentScope 采集 Agent（Java）

基于 **AgentScope Java 2.0 Harness 模式**（`HarnessAgent` + `Toolkit`）的 Spring Boot 应用：persona / 输出规约写在**工作区 `AGENTS.md`**（Harness 的「智能体定义事实来源」，可运行期编辑、无需改码），自定义工具 `web_search` / `fetch_page`（Jsoup 抽取正文）。需 **JDK 17+（推荐 21）与 Maven 3.9+**，首次运行自动下载依赖。

```bash
cd agent
cp .env.example .env        # 一般只需确认 SPRINGBOOT_BASE_URL / AGENT_TOKEN
mvn spring-boot:run                                          # 常驻消费
mvn spring-boot:run -Dspring-boot.run.arguments=--once       # 只处理一个任务（联调）
```

> 工作区目录由 `AGENTSCOPE_WORKSPACE` 控制（默认 `.agentscope/workspace`）；首次启动会把内置 persona 写入其下的 `AGENTS.md`，之后可直接编辑。

> AI 连接（服务商 / 接口地址 / API Key）与模型在后台「AI 配置」页面维护：保存连接后可**拉取远端模型**、一次导入多个模型并设默认。创建抓取任务时也可单独选择模型，未选则用默认模型。Agent 每个任务执行前会拉取对应模型参数，**改完即生效、无需重启 Agent**。`.env` 中的 `DASHSCOPE_*` 仅在后端不可用时作为回退。

任务获取方式二选一（`.env` 中 `AGENT_TASK_MODE`）：

| 模式 | 说明 |
| --- | --- |
| `http` | 轮询 `GET /api/agent/tasks/pull`，适合本地无 Redis |
| `redis` | `BLPOP spectrace:agent:task`，生产环境使用 |

抓取完成后 Agent 调用 `POST /api/agent/callback`，SpringBoot 将结果写入【待审核数据表】，管理员在后台「待审核数据」中修正并审核通过后，才迁移到正式机型库。

## 生产部署

### 方式一：Docker Compose

编排由一份主文件 + 两份可选覆盖文件组成：

| 文件 | 内容 |
| --- | --- |
| `docker-compose.yml` | 应用：`backend`(8080 内网) + `frontend`(Nginx，宿主机 `${HTTP_PORT:-80}`) + 可选 `agent`；默认拉取 GHCR 镜像 |
| `docker-compose.build.yml` | 覆盖文件：为各服务补充 `build` 上下文（改为从源码构建） |
| `docker-compose.infra.yml` | 叠加文件：内置 `mysql` + `redis`（默认连外部库，无需此文件） |

```bash
cp .env.example .env      # 按需修改；生产务必改 JWT_SECRET / AGENT_TOKEN / 数据库密码
```

**A. 使用 GHCR 镜像部署（推荐，服务器无需源码）**

```bash
# 外部 MySQL / Redis（默认）：先对目标库执行 sql/schema-mysql.sql 建库建表
docker compose up -d

# 内置 MySQL / Redis（首次启动自动建库建表）
docker compose -f docker-compose.yml -f docker-compose.infra.yml up -d
```

- 镜像：`ghcr.io/jingyuan9527/spectrace-{backend,frontend,agent}`，版本由 `.env` 的 `IMAGE_TAG` 控制（默认 `latest`）
- `.env` 配置 `MYSQL_HOST/PORT/DATABASE/USERNAME/PASSWORD` 与 `REDIS_HOST/PORT/PASSWORD`；默认 `host.docker.internal`（宿主机），远程库填 IP / 域名（Linux 由 `extra_hosts: host-gateway` 支持）
- 公共镜像可直接拉取；若包为私有，先登录：`echo <PAT> | docker login ghcr.io -u <用户名> --password-stdin`（PAT 需 `read:packages`）

**B. 从源码构建（有源码 / 需改代码）**

```bash
# 外部库
docker compose -f docker-compose.yml -f docker-compose.build.yml up -d --build
# 内置库
docker compose -f docker-compose.yml -f docker-compose.build.yml -f docker-compose.infra.yml up -d --build
```

**通用说明**

- 访问 `http://<服务器IP>:<HTTP_PORT>`，后台 `/#/admin/login`（`admin / admin123`，登录后请立即改密）
- 采集 Agent 默认不启动；需要时追加 `--profile agent`
- 可先 `pull` 更新镜像再启动：如 `docker compose pull && docker compose up -d`
- 常用操作：`docker compose logs -f backend`、`docker compose down`（加 `-v` 可连同数据卷一起删除）
- AI 连接/模型在后台「AI 配置」页面维护，无需改环境变量
- 如需直连后端或 Swagger，放开 `docker-compose.yml` 中 backend 的 `ports` 注释

> 注意：内置库模式下 `./sql/schema-mysql.sql` 仅在 MySQL 空数据卷时执行；后续结构变更需手动迁移或重建数据卷。外部库需自行执行 `sql/schema-mysql.sql` 保证表结构。

### 方式二：传统部署

1. 执行 `sql/schema-mysql.sql` 建库建表
2. 构建并启动后端：`cd backend && mvn -B package -DskipTests`，再 `java -jar target/spectrace-backend-1.0.0.jar --spring.profiles.active=prod`
   - 连接信息建议用环境变量覆盖：`SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` / `SPRING_DATA_REDIS_HOST`
   - 必改密钥：`SPECTRACE_JWT_SECRET`、`SPECTRACE_AGENT_CALLBACK_TOKEN`；上传目录：`SPECTRACE_UPLOAD_DIR=/data/spectrace/uploads`
   - `prod` 段 `spectrace.agent.queue=redis`，任务走 Redis List 队列
3. 前端 `cd frontend && npm run build`，将 `dist/` 交由 Nginx 托管并反向代理 `/api`、`/uploads`（可参考 `frontend/nginx.conf`）

## CI/CD（GitHub Actions）

工作流：`.github/workflows/docker-publish.yml`

- **触发**：推送到 `main`、打 `v*` tag、手动 `workflow_dispatch`；Pull Request 仅构建校验、不推送
- **产物**：`ghcr.io/jingyuan9527/spectrace-backend`、`spectrace-frontend`、`spectrace-agent`
- **架构**：发布时构建 `linux/amd64` + `linux/arm64` 多架构镜像（`docker pull` 自动匹配），PR 仅校验 `linux/amd64`
- **标签**：分支名、tag、`{{version}}` / `{{major}}.{{minor}}`、`sha-<短哈希>`、默认分支的 `latest`
- **权限**：使用内置 `GITHUB_TOKEN`（`packages: write`）登录 GHCR，无需额外 Secrets
- **缓存**：Buildx `type=gha` 分层缓存，加速重复构建
- **注意**：镜像**不含** MySQL / Redis，运行时通过环境变量连接外部实例；首次推送后可在仓库 Packages 页把包的可见性设为 Public（否则部署端需先 `docker login ghcr.io`）

新增组件只需扩展矩阵：

```yaml
strategy:
  matrix:
    component: [ backend, frontend, agent ]
```

## 关键设计

- **动态参数**：`brand / param_group / param_def / device / device_param_value` 分离，新增参数只需向 `param_def` 插入记录，无需 DDL 改表；新增品牌/参数不影响业务代码。
- **对比差异高亮**：后端 `compare` 接口返回每行参数的 `same` 标记，前端据此高亮差异、置灰相同项。
- **截图分享**：`html2canvas` 截取对比/图表区域，支持下载 PNG、复制到剪贴板，水印可开关；截图时自动隐藏交互控件保证画面干净。
- **Agent 解耦**：`TaskQueue` 抽象（内存实现 / Redis 实现按配置切换），`X-Agent-Token` 保护回调接口，抓取结果先进待审核库，人工审核通过才迁移。
- **AI 连接与模型集中管理**：后台「AI 配置」维护多个供应商连接（服务商 / 接口地址 / API Key），支持**拉取远端模型列表**批量导入、导入多个模型并设置默认；创建抓取任务时可指定具体模型，缺省用默认模型。Agent 通过 `/api/agent/ai-config`（可带 `modelId`）运行时拉取并构建大模型（DashScope 原生或 OpenAI 兼容端点，兼容 DeepSeek / Kimi / vLLM / Ollama）。
- **LLM 抽取约束**：Agent 系统提示强制按 `param_def` 定义的 `param_key` 输出 JSON，并对未知键做丢弃清洗。

## 接口文档

- 后端在线文档（Swagger UI）：http://localhost:8080/swagger-ui.html
- SpringBoot ↔ AgentScope 交互契约：`docs/agent-integration-openapi.yaml`

## 开发规约

- 后端：Java 21 / Spring Boot 3.3 / MyBatis-Plus，统一响应体 `R<T>`，全局异常处理 `GlobalExceptionHandler`
- 前端：`npm run build` 验证构建；`npm run typecheck` 做类型检查
- Agent：Java 21 / Spring Boot 3.3 / AgentScope Java 2.0（Harness 模式，Maven 构建）
