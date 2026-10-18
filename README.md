# SpecTrace · 手机参数对比平台

[![Build & Publish Docker Images](https://github.com/jingyuan9527/SpecTrace/actions/workflows/docker-publish.yml/badge.svg)](https://github.com/jingyuan9527/SpecTrace/actions/workflows/docker-publish.yml)

面向数码爱好者的手机参数数据库：收录多品牌机型参数，支持**多机型横向对比**、**单参数跨代历史演变**、**对比结果一键截图分享**；数据来源为管理员手动录入 + 内嵌 AgentScope AI Agent 自动抓取（抓取结果必须人工审核后入库）。

> 仓库地址：<https://github.com/jingyuan9527/SpecTrace>
> 容器镜像：`ghcr.io/jingyuan9527/spectrace-{backend,frontend}`

## 架构

```
┌──────────────────────────┐   HTTP    ┌────────────────────────────────────────┐
│  Vue3 前端（访客端/后台）│ ────────► │  SpringBoot 3.x 后端（单体应用）        │
│                          │ ◄──────── │   业务：MyBatis-Plus / JWT / RBAC       │ ──► MySQL（主业务库）
└──────────────────────────┘           │   采集：内嵌 AgentScope Java 2 Agent    │ ──► Redis（缓存 + 任务队列）
                                       └────────────────────────────────────────┘
```

采集 Agent 与业务后端**同一进程、同一份镜像**：任务经队列（内存 / Redis）投递，Agent 直接调用后端 Service 完成「搜索 → 抓取 → LLM 结构化抽取 → 写待审核库」，无 HTTP 往返，也无需单独部署 Agent 服务。

**职责精简**：Agent 不直接访问数据库，只负责采集与结构化；入库、审核状态流转仍全部由 SpringBoot 掌管。
**数据存储解耦**：MySQL / Redis 支持**外部托管**（默认）或由 Docker 内置，二选一，见下文部署说明。

## 目录结构

```
SpecTrace/
├── backend/         SpringBoot 3.3 + MyBatis-Plus + 内嵌采集 Agent（默认 H2 内存库启动）+ Dockerfile
├── frontend/        Vue3 + Element Plus + TailwindCSS + ECharts + html2canvas + Dockerfile/nginx.conf
├── sql/             MySQL 生产库建表脚本
├── .github/workflows/docker-publish.yml   构建并推送镜像到 GHCR
├── docker-compose.yml       应用编排（默认拉取 GHCR 镜像，连接外部 MySQL/Redis）
├── docker-compose.build.yml 源码构建覆盖文件
├── docker-compose.infra.yml 内置 MySQL/Redis 叠加文件
└── .env.example             部署环境变量样例
```

## 快速启动（本地开发）

### 1. 后端（内含采集 Agent，无需安装 MySQL / Redis）

```bash
cd backend
mvn spring-boot:run
```

- 默认 `dev` profile，使用 H2 内存库并自动建表、写入种子数据
- 端口 `8080`，接口文档：http://localhost:8080/swagger-ui.html
- H2 控制台：http://localhost:8080/h2-console （JDBC URL `jdbc:h2:mem:spectrace`，用户 `sa`，空密码）
- 初始超级管理员：`admin / admin123`（启动时以 BCrypt 写入）
- **采集 Agent 随应用启动常驻消费任务**：先在后台「AI 配置」保存连接与模型（填好 API Key），再到「抓取任务」提交任务即可自动采集
- 关闭内嵌 Agent：启动加 `--spectrace.agent.enabled=false`（或环境变量 `SPECTRACE_AGENT_ENABLED=false`）
- 只处理一个任务（联调）：`mvn spring-boot:run -Dspring-boot.run.arguments=--once`

### 2. 前端

```bash
cd frontend
npm install
npm run dev
```

- 访问 http://localhost:5173 ，已配置 `/api`、`/uploads` 代理到后端 8080
- 访客端：`/#/` 首页、`/#/devices` 机型库、`/#/compare` 对比、`/#/history` 参数历史
- 管理后台：`/#/admin/login`

## 采集 Agent（内嵌）

基于 **AgentScope Java 2.0 Harness 模式**（`HarnessAgent` + `Toolkit`）：persona / 输出规约写在**工作区 `AGENTS.md`**（Harness 的「智能体定义事实来源」，运行期可直接编辑、无需改码），自定义工具 `web_search` / `fetch_page`（Jsoup 抽取正文）。

- 工作区目录由 `AGENTSCOPE_WORKSPACE` 控制（默认 `.agentscope/workspace`），首次启动把内置 persona 写入其下 `AGENTS.md`
- 任务获取实现由 `spectrace.agent.queue` 决定：`memory`（本地开发，进程内队列）/ `redis`（生产，`BLPOP spectrace:agent:task`）；后端投递与 Agent 消费共用同一实现
- 每个任务执行前解析一次 AI 模型参数，后台「AI 配置」改完**下个任务即生效**，无需重启
- AI 连接（服务商 / 接口地址 / API Key）与模型在后台「AI 配置」页面维护：保存连接后可**拉取远端模型**、一次导入多个模型并设默认；创建抓取任务时可指定模型，缺省用默认模型

可调环境变量（`application.yml` 中均有同名默认值）：

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `SPECTRACE_AGENT_ENABLED` | `true` | 是否启用内嵌采集 Agent |
| `AGENTSCOPE_WORKSPACE` | `.agentscope/workspace` | Harness 工作区目录 |
| `POLL_INTERVAL_SECONDS` | `5` | 阻塞等待任务的超时（秒） |
| `TASK_TIMEOUT_SECONDS` | `180` | 单任务最长耗时（秒） |
| `CRAWL_INTERVAL_SECONDS` | `2` | 每次外网请求间隔（秒），抓取风控 |
| `SEARCH_RESULT_LIMIT` | `5` | 搜索结果条数上限 |
| `SERPER_API_KEY` | 空 | 留空用 DuckDuckGo 免 key 抓取，填入则用 Serper |
| `DASHSCOPE_API_KEY` / `DASHSCOPE_MODEL` | 空 / `qwen-max` | 后台未配置 AI 连接时的兜底 |

抓取结果写入【待审核数据表】，管理员在后台「待审核数据」中修正并审核通过后，才迁移到正式机型库。

## 生产部署

### 方式一：Docker Compose

编排由一份主文件 + 两份可选覆盖文件组成：

| 文件 | 内容 |
| --- | --- |
| `docker-compose.yml` | 应用：`backend`(8080 内网，含采集 Agent) + `frontend`(Nginx，宿主机 `${HTTP_PORT:-80}`)；默认拉取 GHCR 镜像 |
| `docker-compose.build.yml` | 覆盖文件：为各服务补充 `build` 上下文（改为从源码构建） |
| `docker-compose.infra.yml` | 叠加文件：内置 `mysql` + `redis`（默认连外部库，无需此文件） |

```bash
cp .env.example .env      # 按需修改；生产务必改 JWT_SECRET / 数据库密码
```

**A. 使用 GHCR 镜像部署（推荐，服务器无需源码）**

```bash
# 外部 MySQL / Redis（默认）：先对目标库执行 sql/schema-mysql.sql 建库建表
docker compose up -d

# 内置 MySQL / Redis（首次启动自动建库建表）
docker compose -f docker-compose.yml -f docker-compose.infra.yml up -d
```

**B. 从源码构建（有源码 / 需改代码）**

```bash
# 外部库
docker compose -f docker-compose.yml -f docker-compose.build.yml up -d --build
# 内置库
docker compose -f docker-compose.yml -f docker-compose.build.yml -f docker-compose.infra.yml up -d --build
```

- 镜像：`ghcr.io/jingyuan9527/spectrace-{backend,frontend}`，版本由 `.env` 的 `IMAGE_TAG` 控制（默认 `latest`）
- `.env` 配置 `MYSQL_HOST/PORT/DATABASE/USERNAME/PASSWORD` 与 `REDIS_HOST/PORT/PASSWORD`；默认 `host.docker.internal`（宿主机），远程库填 IP / 域名
- 采集 Agent 内嵌在后端，无需额外服务或内部令牌；如不希望自动采集，设 `SPECTRACE_AGENT_ENABLED=false`
- 公共镜像可直接拉取；若包为私有，先登录：`echo <PAT> | docker login ghcr.io -u <用户名> --password-stdin`

**通用说明**

- 访问 `http://<服务器IP>:<HTTP_PORT>`，后台 `/#/admin/login`（`admin / admin123`，登录后请立即改密）
- 可先 `pull` 更新镜像再启动：如 `docker compose pull && docker compose up -d`
- 常用操作：`docker compose logs -f backend`、`docker compose down`（加 `-v` 可连同数据卷一起删除）
- AI 连接/模型在后台「AI 配置」页面维护，无需改环境变量
- 如需直连后端或 Swagger，放开 `docker-compose.yml` 中 backend 的 `ports` 注释

> 注意：内置库模式下 `./sql/schema-mysql.sql` 仅在 MySQL 空数据卷时执行；后续结构变更需手动迁移或重建数据卷。外部库需自行执行 `sql/schema-mysql.sql` 保证表结构。

### 方式二：传统部署（单个 Jar）

1. 执行 `sql/schema-mysql.sql` 建库建表
2. 构建并启动后端：`cd backend && mvn -B package -DskipTests`，再 `java -jar target/spectrace-backend-1.0.0.jar --spring.profiles.active=prod`
   - 该 Jar **已内含采集 Agent**，无需单独部署
   - 连接信息建议用环境变量覆盖：`SPRING_DATASOURCE_URL` / `SPRING_DATASOURCE_USERNAME` / `SPRING_DATASOURCE_PASSWORD` / `SPRING_DATA_REDIS_HOST`
   - 必改密钥：`SPECTRACE_JWT_SECRET`；上传目录：`SPECTRACE_UPLOAD_DIR=/data/spectrace/uploads`
   - `prod` 段 `spectrace.agent.queue=redis`，任务走 Redis List 队列；如需关闭采集：`SPECTRACE_AGENT_ENABLED=false`
3. 前端 `cd frontend && npm run build`，将 `dist/` 交由 Nginx 托管并反向代理 `/api`、`/uploads`（可参考 `frontend/nginx.conf`）

## CI/CD（GitHub Actions）

工作流：`.github/workflows/docker-publish.yml`

- **触发**：推送到 `main`、打 `v*` tag、手动 `workflow_dispatch`；Pull Request 仅构建校验、不推送
- **产物**：`ghcr.io/jingyuan9527/spectrace-backend`（含采集 Agent）、`spectrace-frontend`
- **架构**：发布时构建 `linux/amd64` + `linux/arm64` 多架构镜像（`docker pull` 自动匹配），PR 仅校验 `linux/amd64`
- **标签**：分支名、tag、`{{version}}` / `{{major}}.{{minor}}`、`sha-<短哈希>`、默认分支的 `latest`
- **权限**：使用内置 `GITHUB_TOKEN`（`packages: write`）登录 GHCR，无需额外 Secrets
- **缓存**：Buildx `type=gha` 分层缓存，加速重复构建
- **注意**：镜像**不含** MySQL / Redis，运行时通过环境变量连接外部实例；首次推送后可在仓库 Packages 页把包的可见性设为 Public（否则部署端需先 `docker login ghcr.io`）

新增组件只需扩展矩阵：

```yaml
strategy:
  matrix:
    component: [ backend, frontend ]
```

## 关键设计

- **动态参数**：`brand / param_group / param_def / device / device_param_value` 分离，新增参数只需向 `param_def` 插入记录，无需 DDL 改表；新增品牌/参数不影响业务代码。
- **对比差异高亮**：后端 `compare` 接口返回每行参数的 `same` 标记，前端据此高亮差异、置灰相同项。
- **截图分享**：`html2canvas` 截取对比/图表区域，支持下载 PNG、复制到剪贴板，水印可开关；截图时自动隐藏交互控件保证画面干净。
- **内嵌采集 Agent**：`TaskQueue` 抽象（内存 / Redis 按配置切换），Agent 与后端同进程直接调用 Service，抓取结果先进待审核库，人工审核通过才迁移；无需单独服务与内部回调令牌。
- **AI 连接与模型集中管理**：后台「AI 配置」维护多个供应商连接（服务商 / 接口地址 / API Key），支持**拉取远端模型列表**批量导入、导入多个模型并设置默认；创建抓取任务时可指定具体模型，缺省用默认模型。Agent 每任务解析并构建大模型（DashScope 原生或 OpenAI 兼容端点，兼容 DeepSeek / Kimi / vLLM / Ollama）。
- **LLM 抽取约束**：Agent 系统提示强制按 `param_def` 定义的 `param_key` 输出 JSON，并对未知键做丢弃清洗。

## 接口文档

- 后端在线文档（Swagger UI）：http://localhost:8080/swagger-ui.html

## 开发规约

- 后端：Java 21 / Spring Boot 3.3 / MyBatis-Plus / AgentScope Java 2.0（Harness，内嵌），统一响应体 `R<T>`，全局异常处理 `GlobalExceptionHandler`
- 前端：`npm run build` 验证构建；`npm run typecheck` 做类型检查
