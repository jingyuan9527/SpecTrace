-- ============================================================
-- SpecTrace 手机参数对比平台 - SQLite 建表脚本
-- 幂等执行：应用每次启动都会执行，已有表/数据不会被覆盖
-- ============================================================

CREATE TABLE IF NOT EXISTS brand (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        VARCHAR(64)  NOT NULL,
    logo        VARCHAR(512) DEFAULT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    status      TINYINT      NOT NULL DEFAULT 1,
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS param_group (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    name        VARCHAR(64) NOT NULL,
    sort_order  INT         NOT NULL DEFAULT 0,
    created_at  DATETIME    DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT     NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS param_def (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    param_key     VARCHAR(64) NOT NULL,
    param_name    VARCHAR(64) NOT NULL,
    group_id      BIGINT      NOT NULL,
    param_type    VARCHAR(16) NOT NULL DEFAULT 'text',
    enum_options  TEXT        DEFAULT NULL,
    unit          VARCHAR(32) DEFAULT NULL,
    sort_order    INT         NOT NULL DEFAULT 0,
    enabled       TINYINT     NOT NULL DEFAULT 1,
    created_at    DATETIME    DEFAULT CURRENT_TIMESTAMP,
    deleted       TINYINT     NOT NULL DEFAULT 0
);

-- 产品线（系列）：人工维护的归类维度，用于「世代回顾」，未归类机型不受影响
CREATE TABLE IF NOT EXISTS device_series (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    brand_id    BIGINT       NOT NULL,
    name        VARCHAR(64)  NOT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    status      TINYINT      NOT NULL DEFAULT 1,
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS device (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    brand_id     BIGINT       NOT NULL,
    -- 所属产品线，可空：未归类机型照常展示，只是不进世代回顾
    series_id    BIGINT       DEFAULT NULL,
    name         VARCHAR(128) NOT NULL,
    release_date DATE         DEFAULT NULL,
    thumbnail    VARCHAR(512) DEFAULT NULL,
    description  TEXT         DEFAULT NULL,
    status       TINYINT      NOT NULL DEFAULT 1,
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS device_param_value (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id     BIGINT   NOT NULL,
    param_def_id  BIGINT   NOT NULL,
    param_value   TEXT     DEFAULT NULL,
    -- 字段级溯源：抓取入库时记录来源页面与置信度，人工录入留空
    source_url    VARCHAR(512) DEFAULT NULL,
    confidence    DECIMAL(3,2) DEFAULT NULL,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_device_param UNIQUE (device_id, param_def_id)
);

-- 机型的存储/内存版本与官方发布价格（同一机型可有多行）
CREATE TABLE IF NOT EXISTS device_variant (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id     BIGINT   NOT NULL,
    storage       VARCHAR(32)  NOT NULL,
    ram           VARCHAR(32)  DEFAULT NULL,
    price         DECIMAL(10,2) DEFAULT NULL,
    currency      VARCHAR(8)   NOT NULL DEFAULT 'CNY',
    price_type    VARCHAR(16)  NOT NULL DEFAULT 'LAUNCH',
    sort_order    INTEGER      NOT NULL DEFAULT 0,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_device_variant UNIQUE (device_id, storage, ram)
);

-- 访客提交的参数纠错反馈，处理状态由管理端维护
CREATE TABLE IF NOT EXISTS feedback (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id       BIGINT       NOT NULL,
    device_name     VARCHAR(128) DEFAULT NULL,
    param_def_id    BIGINT       DEFAULT NULL,
    param_key       VARCHAR(64)  DEFAULT NULL,
    param_name      VARCHAR(64)  DEFAULT NULL,
    current_value   TEXT         DEFAULT NULL,
    suggested_value TEXT         DEFAULT NULL,
    description     TEXT         DEFAULT NULL,
    contact         VARCHAR(128) DEFAULT NULL,
    status          VARCHAR(24)  NOT NULL DEFAULT 'OPEN',
    handler_note    TEXT         DEFAULT NULL,
    public_code     VARCHAR(16)  DEFAULT NULL,
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS agent_task (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    model_name   VARCHAR(128) NOT NULL,
    brand_name   VARCHAR(64)  DEFAULT NULL,
    ai_model_id  BIGINT       DEFAULT NULL,
    task_type    VARCHAR(24)  NOT NULL DEFAULT 'DEVICE_DETAIL',
    subscription_id BIGINT    DEFAULT NULL,
    status       VARCHAR(24)  NOT NULL DEFAULT 'QUEUED',
    source_url   VARCHAR(512) DEFAULT NULL,
    result       TEXT         DEFAULT NULL,
    error_msg    TEXT         DEFAULT NULL,
    duration_ms  BIGINT       DEFAULT NULL,
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS agent_pending_data (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id      BIGINT       DEFAULT NULL,
    model_name   VARCHAR(128) NOT NULL,
    brand_name   VARCHAR(64)  DEFAULT NULL,
    params_json  TEXT         DEFAULT NULL,
    source_url   VARCHAR(512) DEFAULT NULL,
    status       VARCHAR(24)  NOT NULL DEFAULT 'PENDING_REVIEW',
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS agent_task_log (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    task_id      BIGINT       NOT NULL,
    level        VARCHAR(16)  NOT NULL DEFAULT 'INFO',
    message      TEXT         DEFAULT NULL,
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS ai_config (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    name         VARCHAR(64)  NOT NULL,
    provider     VARCHAR(32)  NOT NULL DEFAULT 'dashscope',
    base_url     VARCHAR(255) DEFAULT NULL,
    api_key      VARCHAR(255) DEFAULT NULL,
    enabled      TINYINT      NOT NULL DEFAULT 1,
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS ai_model (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    config_id    BIGINT       NOT NULL,
    model        VARCHAR(128) NOT NULL,
    temperature  DECIMAL(3,2) NOT NULL DEFAULT 0.20,
    is_default   TINYINT      NOT NULL DEFAULT 0,
    enabled      TINYINT      NOT NULL DEFAULT 1,
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0
);

-- 采集搜索源配置（单行，id=1）：选 Tavily / SearXNG 后失败自动回落 DuckDuckGo
CREATE TABLE IF NOT EXISTS agent_search_config (
    id               INTEGER PRIMARY KEY AUTOINCREMENT,
    provider         VARCHAR(16)  NOT NULL DEFAULT 'system',
    tavily_api_key   VARCHAR(255) DEFAULT NULL,
    searxng_base_url VARCHAR(255) DEFAULT NULL,
    searxng_token    VARCHAR(255) DEFAULT NULL,
    serper_api_key   VARCHAR(255) DEFAULT NULL,
    created_at       DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at       DATETIME     DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS admin_user (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    username    VARCHAR(64)  NOT NULL,
    password    VARCHAR(128) NOT NULL,
    nickname    VARCHAR(64)  DEFAULT NULL,
    role        VARCHAR(32)  NOT NULL DEFAULT 'ADMIN',
    status      TINYINT      NOT NULL DEFAULT 1,
    must_change_password TINYINT NOT NULL DEFAULT 0,
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS operation_log (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    admin_id     BIGINT      DEFAULT NULL,
    admin_name   VARCHAR(64) DEFAULT NULL,
    action       VARCHAR(64) NOT NULL,
    target_type  VARCHAR(32) DEFAULT NULL,
    target_id    BIGINT      DEFAULT NULL,
    detail       TEXT        DEFAULT NULL,
    created_at   DATETIME    DEFAULT CURRENT_TIMESTAMP
);

-- 访客搜索词按天聚合（T102）：只记词 + 次数 + 空结果次数，不记 IP 与任何访客标识
CREATE TABLE IF NOT EXISTS search_log (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    keyword      VARCHAR(64) NOT NULL,
    search_date  DATE        NOT NULL,
    search_count INTEGER     NOT NULL DEFAULT 0,
    zero_count   INTEGER     NOT NULL DEFAULT 0,
    UNIQUE (keyword, search_date)
);

-- 通用日计数（T103）：对比发起次数等单值指标，key+date 唯一累加
CREATE TABLE IF NOT EXISTS metric_daily (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    metric_key VARCHAR(64) NOT NULL,
    metric_date DATE      NOT NULL,
    value      INTEGER    NOT NULL DEFAULT 0,
    UNIQUE (metric_key, metric_date)
);

-- 系列订阅抓取（T88）：按周期自动创建「系列发现」任务，发布季自动跟进；series 唯一
CREATE TABLE IF NOT EXISTS agent_subscription (
    id              INTEGER PRIMARY KEY AUTOINCREMENT,
    series_id       BIGINT      NOT NULL,
    interval_months INTEGER     NOT NULL DEFAULT 3,
    ai_model_id     BIGINT      DEFAULT NULL,
    enabled         TINYINT     NOT NULL DEFAULT 1,
    last_run_at     DATETIME    DEFAULT NULL,
    created_at      DATETIME    DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME    DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (series_id)
);

-- 访客行为统计：机型浏览量按天聚合（详情访问与对比发起），热门排序与数据健康度补录优先级的数据源
CREATE TABLE IF NOT EXISTS device_view_daily (
    id         INTEGER PRIMARY KEY AUTOINCREMENT,
    device_id  BIGINT     NOT NULL,
    view_date  DATE       NOT NULL,
    pv         INTEGER    NOT NULL DEFAULT 0,
    UNIQUE (device_id, view_date)
);

-- AI 绘图供应商：独立于对话模型配置，对接 OpenAI 兼容 /images/generations
CREATE TABLE IF NOT EXISTS ai_image_provider (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    name         VARCHAR(64)  NOT NULL,
    base_url     VARCHAR(255) NOT NULL,
    api_key      VARCHAR(255) DEFAULT NULL,
    model        VARCHAR(128) NOT NULL,
    size         VARCHAR(32)  NOT NULL DEFAULT '1024x1536',
    quality      VARCHAR(16)  DEFAULT NULL,
    enabled      TINYINT      NOT NULL DEFAULT 1,
    is_default   TINYINT      NOT NULL DEFAULT 0,
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0
);

-- AI 绘图提示词模板：内置模板只读不可删，用户可复制后改；默认模板全局唯一
CREATE TABLE IF NOT EXISTS ai_image_prompt (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    name         VARCHAR(64)  NOT NULL,
    content      TEXT         NOT NULL,
    builtin      TINYINT      NOT NULL DEFAULT 0,
    is_default   TINYINT      NOT NULL DEFAULT 0,
    enabled      TINYINT      NOT NULL DEFAULT 1,
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0
);

-- AI 绘图历史：落库每次生成的提示词快照与产物地址，供后台追溯与清理文件
CREATE TABLE IF NOT EXISTS ai_image_job (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    provider_id   BIGINT       DEFAULT NULL,
    provider_name VARCHAR(64)  DEFAULT NULL,
    model         VARCHAR(128) DEFAULT NULL,
    prompt_id     BIGINT       DEFAULT NULL,
    prompt_name   VARCHAR(64)  DEFAULT NULL,
    prompt_text   TEXT         DEFAULT NULL,
    device_names  VARCHAR(512) DEFAULT NULL,
    param_names   VARCHAR(512) DEFAULT NULL,
    status        VARCHAR(24)  NOT NULL DEFAULT 'SUCCESS',
    image_url     VARCHAR(512) DEFAULT NULL,
    error_msg     TEXT         DEFAULT NULL,
    duration_ms   BIGINT       DEFAULT NULL,
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP
);

-- 模型调用明细：逐次记录采集 Agent / AI 摘要 / AI 绘图的请求，供后台统计与排障
CREATE TABLE IF NOT EXISTS ai_call_log (
    id                INTEGER PRIMARY KEY AUTOINCREMENT,
    scene             VARCHAR(24)  NOT NULL,
    provider_kind     VARCHAR(16)  NOT NULL,
    provider_id       BIGINT       DEFAULT NULL,
    provider_name     VARCHAR(128) DEFAULT NULL,
    model_id          BIGINT       DEFAULT NULL,
    model_name        VARCHAR(128) NOT NULL,
    success           TINYINT      NOT NULL DEFAULT 0,
    status_code       INT          DEFAULT NULL,
    latency_ms        BIGINT       NOT NULL DEFAULT 0,
    error_type        VARCHAR(32)  DEFAULT NULL,
    error_msg         VARCHAR(512) DEFAULT NULL,
    prompt_tokens     INT          DEFAULT NULL,
    completion_tokens INT          DEFAULT NULL,
    biz_id            VARCHAR(64)  DEFAULT NULL,
    created_at        DATETIME     DEFAULT CURRENT_TIMESTAMP
);

-- 逻辑删除的软删行不参与唯一约束，删除后允许重建同名记录（SQLite 部分索引）
CREATE UNIQUE INDEX IF NOT EXISTS uk_param_key ON param_def (param_key) WHERE deleted = 0;
CREATE UNIQUE INDEX IF NOT EXISTS uk_username ON admin_user (username) WHERE deleted = 0;

CREATE INDEX IF NOT EXISTS idx_brand_sort ON brand (sort_order);
CREATE INDEX IF NOT EXISTS idx_param_group ON param_def (group_id);
CREATE INDEX IF NOT EXISTS idx_device_brand ON device (brand_id);
-- device.series_id 是后加列，其索引由 SqliteSchemaMigration 在补列之后创建，脚本阶段引用会导致老库启动失败
CREATE UNIQUE INDEX IF NOT EXISTS uk_series_name ON device_series (brand_id, name) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_series_brand ON device_series (brand_id);
CREATE INDEX IF NOT EXISTS idx_device_release ON device (release_date);
CREATE INDEX IF NOT EXISTS idx_device_name ON device (name);
CREATE INDEX IF NOT EXISTS idx_dpv_device ON device_param_value (device_id);
CREATE INDEX IF NOT EXISTS idx_dpv_def ON device_param_value (param_def_id);
CREATE INDEX IF NOT EXISTS idx_variant_device ON device_variant (device_id);
CREATE INDEX IF NOT EXISTS idx_feedback_status ON feedback (status);
CREATE INDEX IF NOT EXISTS idx_feedback_device ON feedback (device_id);
CREATE INDEX IF NOT EXISTS idx_task_status ON agent_task (status);
CREATE INDEX IF NOT EXISTS idx_task_created ON agent_task (created_at);
CREATE INDEX IF NOT EXISTS idx_pending_status ON agent_pending_data (status);
CREATE INDEX IF NOT EXISTS idx_pending_task ON agent_pending_data (task_id);
CREATE INDEX IF NOT EXISTS idx_task_log_task ON agent_task_log (task_id, id);
CREATE INDEX IF NOT EXISTS idx_ai_model_config ON ai_model (config_id);
CREATE INDEX IF NOT EXISTS idx_ai_model_default ON ai_model (is_default);
CREATE INDEX IF NOT EXISTS idx_log_target ON operation_log (target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_log_created ON operation_log (created_at);
CREATE INDEX IF NOT EXISTS idx_view_date ON device_view_daily (view_date);
CREATE INDEX IF NOT EXISTS idx_ai_image_provider_default ON ai_image_provider (is_default);
CREATE INDEX IF NOT EXISTS idx_ai_image_prompt_default ON ai_image_prompt (is_default);
CREATE INDEX IF NOT EXISTS idx_ai_image_job_created ON ai_image_job (created_at);
CREATE INDEX IF NOT EXISTS idx_ai_call_log_created ON ai_call_log (created_at);
CREATE INDEX IF NOT EXISTS idx_ai_call_log_dim ON ai_call_log (provider_kind, provider_id, model_id, created_at);
CREATE INDEX IF NOT EXISTS idx_ai_call_log_scene_biz ON ai_call_log (scene, biz_id, id);

-- ===== 对外 MCP 服务 =====
-- 接入凭据与授权范围：Key 只存哈希与前缀，明文仅在创建/轮换时返回一次
CREATE TABLE IF NOT EXISTS mcp_agent (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    name          VARCHAR(64)  NOT NULL,
    description   VARCHAR(255) DEFAULT NULL,
    api_key_hash  VARCHAR(64)  NOT NULL,
    api_key_prefix VARCHAR(24) NOT NULL,
    role          VARCHAR(24)  NOT NULL DEFAULT 'EDITOR',
    -- 逗号分隔的 scope 列表，* 表示全部；鉴权只认这一列，role 仅作审计标签
    scopes        TEXT         NOT NULL,
    status        INTEGER      NOT NULL DEFAULT 1,
    call_count    BIGINT       NOT NULL DEFAULT 0,
    last_used_at  DATETIME     DEFAULT NULL,
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted       TINYINT      NOT NULL DEFAULT 0
);

-- 工具调用审计：参数截断落库，成功与失败都记，便于回溯 Agent 到底改了什么
CREATE TABLE IF NOT EXISTS mcp_call_log (
    id            INTEGER PRIMARY KEY AUTOINCREMENT,
    agent_id      BIGINT       DEFAULT NULL,
    agent_name    VARCHAR(64)  DEFAULT NULL,
    tool_name     VARCHAR(64)  NOT NULL,
    arguments_json TEXT        DEFAULT NULL,
    status        VARCHAR(16)  NOT NULL,
    error_code    VARCHAR(32)  DEFAULT NULL,
    error_message VARCHAR(512) DEFAULT NULL,
    duration_ms   BIGINT       NOT NULL DEFAULT 0,
    client_name   VARCHAR(128) DEFAULT NULL,
    session_id    VARCHAR(64)  DEFAULT NULL,
    created_at    DATETIME     DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_mcp_agent_name ON mcp_agent (name) WHERE deleted = 0;
CREATE INDEX IF NOT EXISTS idx_mcp_agent_prefix ON mcp_agent (api_key_prefix);
CREATE INDEX IF NOT EXISTS idx_mcp_call_log_agent ON mcp_call_log (agent_id, id);
CREATE INDEX IF NOT EXISTS idx_mcp_call_log_tool ON mcp_call_log (tool_name, id);
CREATE INDEX IF NOT EXISTS idx_mcp_call_log_created ON mcp_call_log (created_at);
