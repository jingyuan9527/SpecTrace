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
    created_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted         TINYINT      NOT NULL DEFAULT 0
);

CREATE TABLE IF NOT EXISTS agent_task (
    id           INTEGER PRIMARY KEY AUTOINCREMENT,
    model_name   VARCHAR(128) NOT NULL,
    brand_name   VARCHAR(64)  DEFAULT NULL,
    ai_model_id  BIGINT       DEFAULT NULL,
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
CREATE INDEX IF NOT EXISTS idx_ai_model_config ON ai_model (config_id);
CREATE INDEX IF NOT EXISTS idx_ai_model_default ON ai_model (is_default);
CREATE INDEX IF NOT EXISTS idx_log_target ON operation_log (target_type, target_id);
CREATE INDEX IF NOT EXISTS idx_log_created ON operation_log (created_at);
