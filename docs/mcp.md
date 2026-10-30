# MCP 服务接入指南

SpecTrace 内置一个 **MCP（Model Context Protocol）服务**，让外部 Agent（Claude Desktop、Cursor、Cherry Studio、Claude Code 等）直接对平台数据做增删改查，而不需要绕后台页面。

- 传输方式：**Streamable HTTP**（MCP 规范推荐的远程传输）
- 端点：`/mcp`（默认 `http://<host>:8080/mcp`，可用 `SPECTRACE_MCP_ENDPOINT` 改）
- 鉴权：**MCP API Key**，与后台账号的 JWT 完全隔离
- 权限：按 Agent 授予 `资源:动作` 级 scope
- 审计：每次调用写入 `mcp_call_log`，每次数据变更写入 `operation_log`

服务随 Spring Boot 应用启动，无需单独部署。不需要该能力时设置 `SPECTRACE_MCP_ENABLED=false`，端点与过滤器会一起下线。

---

## 1. 创建接入凭据

每个 Agent 一份独立 Key，便于单独授权、单独吊销、单独审计。

### 方式 A：后台接口创建（推荐）

用超级管理员账号登录后调用管理接口：

```bash
# 1. 登录拿 JWT
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.data.token')

# 2. 创建只读 Agent（scopes 留空时按 role 给默认值）
curl -s -X POST http://localhost:8080/api/admin/mcp/agents \
  -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"name":"claude-desktop","role":"READ_ONLY","description":"本地桌面客户端"}'
```

响应中的 `data.apiKey` 是**明文 Key，只返回这一次**，请立即保存：

```json
{
  "code": 0,
  "data": {
    "agent": { "id": 1, "name": "claude-desktop", "role": "READ_ONLY", "scopes": ["device:read", "..."] },
    "apiKey": "stmcp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx",
    "warning": "密钥仅显示本次，请立即保存；丢失后只能轮换"
  }
}
```

### 方式 B：启动配置引导

适合容器化部署，第一次启动自动建 Agent：

```bash
SPECTRACE_MCP_BOOTSTRAP_AGENT=claude-desktop
SPECTRACE_MCP_BOOTSTRAP_KEY=stmcp_<40 位随机字母数字>
SPECTRACE_MCP_BOOTSTRAP_ROLE=EDITOR
```

Key 必须是 `stmcp_` + 40 位字母数字（总长 46）。生成方式：

```bash
# bash
echo "stmcp_$(LC_ALL=C tr -dc 'A-Za-z0-9' </dev/urandom | head -c 40)"
```

```powershell
# PowerShell
"stmcp_" + (-join (1..40 | ForEach-Object { 'ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789'[(Get-Random -Maximum 62)] }))
```

同名 Agent 已存在时不会覆盖密钥——避免每次重启把客户端配置打回原形。需要换 Key 用轮换接口。

---

## 2. 客户端配置

凭据通过以下任一请求头传递，优先级 `X-MCP-Key` 高于 `Authorization`：

```
X-MCP-Key: stmcp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
Authorization: Bearer stmcp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
```

> 后台 JWT 也走 `Authorization: Bearer`，服务端只接管 `stmcp_` 前缀的 token，两者不会串。

### Cursor

项目内 `.cursor/mcp.json` 或全局 `~/.cursor/mcp.json`：

```json
{
  "mcpServers": {
    "spectrace": {
      "url": "http://localhost:8080/mcp",
      "headers": { "X-MCP-Key": "stmcp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" }
    }
  }
}
```

### Claude Code

```bash
claude mcp add --transport http spectrace http://localhost:8080/mcp \
  --header "X-MCP-Key: stmcp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx"
```

### Claude Desktop

新版可直接在 **Settings → Connectors → Add custom connector** 填 `http://localhost:8080/mcp`；需要自定义请求头时用 `mcp-remote` 桥接：

```json
{
  "mcpServers": {
    "spectrace": {
      "command": "npx",
      "args": [
        "-y", "mcp-remote", "http://localhost:8080/mcp",
        "--header", "X-MCP-Key:${SPECTRACE_MCP_KEY}"
      ],
      "env": { "SPECTRACE_MCP_KEY": "stmcp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx" }
    }
  }
}
```

### Cherry Studio / 其它客户端

添加 MCP 服务器 → 类型选 **Streamable HTTP** → URL 填 `http://<host>:8080/mcp` → 自定义请求头加 `X-MCP-Key`。

### 接入自检

任何客户端接入后先调 `spectrace_whoami`，应返回当前 Agent 名称与 scope 列表。看到 `FORBIDDEN` 时说明是授权不足，不是参数写错。

---

## 3. 权限模型

### scope 列表

| scope | 覆盖工具 |
| --- | --- |
| `device:read` | `spectrace_device_list` / `spectrace_device_get` |
| `device:write` | `spectrace_device_create` / `spectrace_device_update` / `spectrace_device_delete` / `spectrace_device_batch_update_status` |
| `brand:read` / `brand:write` | 品牌查询 / 增删改 |
| `series:read` / `series:write` | 系列查询 / 增删改 |
| `param:read` / `param:write` | 参数分组与参数定义查询 / 增删改 |
| `review:read` / `review:write` | 待审核数据查询 / 审核通过、丢弃 |
| `feedback:read` / `feedback:write` | 访客纠错查询 / 处理 |
| `stats:read` | 数据概览、操作日志、MCP 调用日志 |

### 角色默认值

| role | 默认 scopes |
| --- | --- |
| `READ_ONLY` | 全部 `*:read`（7 个） |
| `EDITOR` | 除 `feedback:write` 外的全部 |
| `ADMIN` | 全部 |

显式传 `scopes` 时以传入值为准，`["*"]` 表示全部。角色本身只作审计标签，**鉴权只认 scopes**。

---

## 4. 工具清单

共 37 个工具，命名统一为 `spectrace_<资源>_<动作>`。

### 身份

| 工具 | 必填参数 | 说明 |
| --- | --- | --- |
| `spectrace_whoami` | — | 返回 Agent 名称、角色、scope 列表 |

### 品牌

| 工具 | 必填参数 | 说明 |
| --- | --- | --- |
| `spectrace_brand_list` | — | 可选 `status` / `keyword`，一次返回全部 |
| `spectrace_brand_get` | `id` | |
| `spectrace_brand_create` | `name` | 可选 `logo` / `sortOrder` / `status` |
| `spectrace_brand_update` | `id` | 只覆盖传入字段 |
| `spectrace_brand_delete` | `id` | 逻辑删除 |

### 系列

| 工具 | 必填参数 | 说明 |
| --- | --- | --- |
| `spectrace_series_list` | — | 可选 `brandId` / `status` / `keyword` |
| `spectrace_series_get` | `id` | |
| `spectrace_series_create` | `brandId`、`name` | 同品牌内名称唯一 |
| `spectrace_series_update` | `id` | **所属品牌不可修改** |
| `spectrace_series_delete` | `id` | 系列下仍有在册机型时拒绝 |

### 机型

| 工具 | 必填参数 | 说明 |
| --- | --- | --- |
| `spectrace_device_list` | — | `current` / `size` / `brandId` / `seriesId`（0 = 未归类）/ `year` / `keyword` / `status` |
| `spectrace_device_get` | `id` | 返回机型、品牌、系列、参数、溯源、存储版本、同系列机型 |
| `spectrace_device_create` | `name`、`brandId` | 可选 `seriesId` / `releaseDate` / `thumbnail` / `description` / `status` / `params` / `variants` |
| `spectrace_device_update` | `id` | 同上可选字段；未传字段保持不变。参数支持 `params`（全量覆盖）/ `paramsPatch`（只改动传入的键）二选一，`dryRun=true` 只预览变更；回执额外带 `paramsChange` |
| `spectrace_device_delete` | `id` | 逻辑删除 + 清理参数与版本 |
| `spectrace_device_batch_update_status` | `ids`、`status` | 批量上下架，不触碰参数 |

### 参数元数据

| 工具 | 必填参数 | 说明 |
| --- | --- | --- |
| `spectrace_param_group_list` | — | |
| `spectrace_param_group_create` | `name` | 可选 `sortOrder` |
| `spectrace_param_group_update` | `id` | |
| `spectrace_param_group_delete` | `id` | 分组下还有参数定义时拒绝 |
| `spectrace_param_def_list` | — | 可选 `groupId` / `enabled` |
| `spectrace_param_def_create` | `paramKey`、`paramName`、`groupId`、`paramType` | `paramType`：text / number / image / date / select |
| `spectrace_param_def_update` | `id` | **paramKey 不可修改** |
| `spectrace_param_def_delete` | `id` | 逻辑删除 |

### 待审核数据

| 工具 | 必填参数 | 说明 |
| --- | --- | --- |
| `spectrace_pending_list` | — | 默认只返回 `PENDING_REVIEW`，`status=ALL` 看全部 |
| `spectrace_pending_get` | `id` | 含库中现有值 diff |
| `spectrace_pending_approve` | `id`、`brandId` | 其余字段不传时回落到抓取结果 |
| `spectrace_pending_discard` | `id` | |
| `spectrace_pending_batch_approve` | `ids` | 逐条独立事务，返回成功数与失败明细 |
| `spectrace_pending_batch_discard` | `ids` | |

### 纠错反馈

| 工具 | 必填参数 | 说明 |
| --- | --- | --- |
| `spectrace_feedback_list` | — | 可选 `status`：OPEN / RESOLVED / IGNORED |
| `spectrace_feedback_get` | `id` | |
| `spectrace_feedback_update_status` | `id`、`status` | 可选 `note`，写入 handlerNote |

### 统计与审计

| 工具 | 必填参数 | 说明 |
| --- | --- | --- |
| `spectrace_stats_overview` | — | 品牌/系列/机型/参数/待审核/纠错/任务/MCP 调用计数 |
| `spectrace_operation_log_list` | — | `action` / `targetType` / `source`（ALL / MCP / MANUAL） |
| `spectrace_mcp_call_log_list` | — | `agentId` / `toolName` / `status`（SUCCESS / ERROR / DENIED） |

所有分页工具使用 `current`（从 1 开始）+ `size`，`size` 会被压到 `SPECTRACE_MCP_MAX_PAGE_SIZE`（默认 100）以内。

---

## 5. 返回结构与错误码

成功与失败都返回同一结构，放在 MCP 的 `structuredContent` 里，同时以 JSON 文本复制到 `content`，兼容只读文本通道的客户端：

```json
{
  "success": true,
  "code": "OK",
  "message": "success",
  "data": { "...": "工具各自的业务数据" },
  "meta": {
    "tool": "spectrace_device_get",
    "agent": "claude-desktop",
    "scopes": ["device:read"],
    "durationMs": 12
  }
}
```

失败时 `isError=true`，`code` 取值：

| code | 含义 | Agent 应对 |
| --- | --- | --- |
| `INVALID_ARGUMENT` | 参数缺失、类型错误、日期格式错误 | 按 `message` 修正参数后重试 |
| `FORBIDDEN` | 当前 Agent 缺少该工具所需 scope | 不要重试，找管理员授权 |
| `NOT_FOUND` | 目标记录不存在或已删除 | 核对 id |
| `BUSINESS_ERROR` | 业务规则拒绝（系列不属于该品牌、系列下仍有在册机型等） | 按 `message` 调整数据 |
| `CONFLICT` | 唯一约束冲突（同名品牌/系列、重复 paramKey） | 先查询再写入 |
| `RETRY_LATER` | 数据库写锁排队超时（并发写密集或正在备份） | 退避（建议 200ms–1s）后重试同一调用，不是数据问题 |
| `INTERNAL_ERROR` | 服务内部异常 | 稍后重试；持续失败查服务端日志 |

HTTP 层只有一种失败：未带 Key 或 Key 无效时返回 `401`（此时还没进入 MCP 协议层）：

```json
{ "code": 401, "message": "MCP API Key 无效或已停用" }
```

---

## 6. 写入语义（Agent 必须知道）

1. **params 是全量覆盖**：`spectrace_device_update` 传入的 `paramKey` 集合就是最终参数集合，未提交或值为空白的键会被删除。不传 `params` 才表示「不改动」；传 `{}` 表示清空全部参数。
2. **补参用 `paramsPatch`**：只改动传入的键，其余旧值原样保留；`"paramKey": ""` 表示删除该键。它与 `params` 互斥。
3. **先看回执再批量**：更新（含 `dryRun=true` 的预览）都会返回 `paramsChange.added / updated / removed / ignored`；`removed` 非空说明本次删了参数；`ignored` 是 `param_def` 里不存在的键。
4. **variants 同理**：不传不动，传 `[]` 清空。
5. **paramKey 必须先查**：`params` 里不存在的 `paramKey` 会被静默忽略（回执的 `ignored` 会列出来），先用 `spectrace_param_def_list` 确认。
6. **status**：`device.status=1` 上架（对外可见），`0` 下架（对外 404）。更新时不传则保持原值。
7. **seriesId=0** 表示取消归类（机型保留，只是不进世代回顾）。
8. **品牌与系列**：系列必须属于机型所选品牌，跨品牌会直接拒绝；系列所属品牌不可修改。
9. **删除是逻辑删除**：数据仍在库里，唯一索引带 `WHERE deleted = 0`，删除后可以重建同名记录。

---

## 7. 审计

| 表 | 记录内容 | 查询入口 |
| --- | --- | --- |
| `operation_log` | 数据变更：`adminName = mcp:<agent>`、`adminId = 0`、action 如 `CREATE_DEVICE` | `spectrace_operation_log_list`（`source=MCP`）或后台操作日志页 |
| `mcp_call_log` | 每次工具调用：入参（脱敏、超长截断）、状态、错误码、耗时、客户端名、会话 id | `spectrace_mcp_call_log_list` 或 `GET /api/admin/mcp/logs` |

`mcp_call_log` 按 `SPECTRACE_RETENTION_MCP_CALL_DAYS`（默认 30 天）随每日保留策略清理，`operation_log` 默认保留 90 天。

日志写入失败不会影响工具调用结果：审计只降级可观测性，不应让业务回滚。

---

## 8. 管理接口

后台「MCP 接入」页（`/admin/mcp`）提供同一套能力的可视化操作：签发后一次性展示明文 Key 与客户端配置片段，支持编辑授权范围、轮换、吊销，以及查看调用日志详情（含脱敏入参）。ADMIN 只读，写操作仅 SUPER_ADMIN。以下接口供脚本化与集成使用。

| 方法 | 路径 | 权限 | 说明 |
| --- | --- | --- | --- |
| GET | `/api/admin/mcp/agents` | ADMIN / SUPER_ADMIN | Agent 列表（不含明文 Key） |
| POST | `/api/admin/mcp/agents` | SUPER_ADMIN | 创建 Agent，返回一次性明文 Key |
| PUT | `/api/admin/mcp/agents/{id}` | SUPER_ADMIN | 改名称、描述、role、scopes、启停 |
| POST | `/api/admin/mcp/agents/{id}/rotate-key` | SUPER_ADMIN | 轮换 Key，旧 Key 立即失效 |
| DELETE | `/api/admin/mcp/agents/{id}` | SUPER_ADMIN | 删除 Agent，其 Key 立即失效 |
| GET | `/api/admin/mcp/logs` | ADMIN / SUPER_ADMIN | 调用日志分页（`agentId` / `toolName` / `status`） |

管理接口本身也写 `operation_log`（`CREATE_MCP_AGENT` 等），签发凭据的动作可追溯。

---

## 9. 调试

```bash
# 1. 初始化，记下响应头 Mcp-Session-Id
curl -i -X POST http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H 'X-MCP-Key: stmcp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"curl","version":"1.0"}}}'

# 2. 发送 initialized 通知（HttpServletResponse 202 即成功）
curl -s -o /dev/null -w '%{http_code}\n' -X POST http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H 'X-MCP-Key: stmcp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx' \
  -H 'Mcp-Session-Id: <上一步的 session id>' \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'

# 3. 调用工具
curl -s -X POST http://localhost:8080/mcp \
  -H 'Content-Type: application/json' \
  -H 'Accept: application/json, text/event-stream' \
  -H 'X-MCP-Key: stmcp_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx' \
  -H 'Mcp-Session-Id: <session id>' \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"spectrace_whoami","arguments":{}}}'
```

响应可能是 `text/event-stream`（带 `event:message` / `data:` 前缀），也可能是纯 JSON，取决于 `Accept`。客户端按 SSE 解析即可。

---

## 10. 配置项

| 变量 | 默认 | 说明 |
| --- | --- | --- |
| `SPECTRACE_MCP_ENABLED` | `true` | 是否启用 MCP 端点 |
| `SPECTRACE_MCP_ENDPOINT` | `/mcp` | 端点路径 |
| `SPECTRACE_MCP_SERVER_NAME` / `_VERSION` | `spectrace` / `1.0.0` | 服务标识，客户端可见 |
| `SPECTRACE_MCP_MAX_PAGE_SIZE` | `100` | 单页返回上限 |
| `SPECTRACE_MCP_LOG_ARG_MAX_LENGTH` | `2000` | 调用日志入参截断长度 |
| `SPECTRACE_MCP_BOOTSTRAP_AGENT` | 空 | 启动时自动创建的 Agent 名称 |
| `SPECTRACE_MCP_BOOTSTRAP_KEY` | 空 | 该 Agent 的明文 Key（`stmcp_` + 40 位） |
| `SPECTRACE_MCP_BOOTSTRAP_ROLE` | `EDITOR` | 该 Agent 的角色 |
| `SPECTRACE_RETENTION_MCP_CALL_DAYS` | `30` | 调用日志保留天数 |
