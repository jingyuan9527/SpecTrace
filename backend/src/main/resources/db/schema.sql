-- ============================================================
-- SpecTrace 手机参数对比平台 - H2 开发库建表脚本 (MODE=MySQL)
-- 生产 MySQL 版本见 /sql/schema-mysql.sql
-- ============================================================

DROP TABLE IF EXISTS brand;
CREATE TABLE brand (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64)  NOT NULL,
    logo        VARCHAR(512) DEFAULT NULL,
    sort_order  INT          NOT NULL DEFAULT 0,
    status      TINYINT      NOT NULL DEFAULT 1,
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS param_group;
CREATE TABLE param_group (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64) NOT NULL,
    sort_order  INT         NOT NULL DEFAULT 0,
    created_at  DATETIME    DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS param_def;
CREATE TABLE param_def (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    param_key     VARCHAR(64) NOT NULL,
    param_name    VARCHAR(64) NOT NULL,
    group_id      BIGINT      NOT NULL,
    param_type    VARCHAR(16) NOT NULL DEFAULT 'text',
    enum_options  TEXT        DEFAULT NULL,
    unit          VARCHAR(32) DEFAULT NULL,
    sort_order    INT         NOT NULL DEFAULT 0,
    enabled       TINYINT     NOT NULL DEFAULT 1,
    created_at    DATETIME    DEFAULT CURRENT_TIMESTAMP,
    deleted       TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS device;
CREATE TABLE device (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    brand_id     BIGINT       NOT NULL,
    name         VARCHAR(128) NOT NULL,
    release_date DATE         DEFAULT NULL,
    thumbnail    VARCHAR(512) DEFAULT NULL,
    description  TEXT         DEFAULT NULL,
    status       TINYINT      NOT NULL DEFAULT 1,
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS device_param_value;
CREATE TABLE device_param_value (
    id            BIGINT   NOT NULL AUTO_INCREMENT,
    device_id     BIGINT   NOT NULL,
    param_def_id  BIGINT   NOT NULL,
    param_value   TEXT     DEFAULT NULL,
    created_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS agent_task;
CREATE TABLE agent_task (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    model_name   VARCHAR(128) NOT NULL,
    brand_name   VARCHAR(64)  DEFAULT NULL,
    ai_model_id  BIGINT       DEFAULT NULL,
    status       VARCHAR(24)  NOT NULL DEFAULT 'QUEUED',
    source_url   VARCHAR(512) DEFAULT NULL,
    result       TEXT         DEFAULT NULL,
    error_msg    TEXT         DEFAULT NULL,
    duration_ms  BIGINT       DEFAULT NULL,
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS agent_pending_data;
CREATE TABLE agent_pending_data (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    task_id      BIGINT       DEFAULT NULL,
    model_name   VARCHAR(128) NOT NULL,
    brand_name   VARCHAR(64)  DEFAULT NULL,
    params_json  TEXT         DEFAULT NULL,
    source_url   VARCHAR(512) DEFAULT NULL,
    status       VARCHAR(24)  NOT NULL DEFAULT 'PENDING_REVIEW',
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS ai_config;
CREATE TABLE ai_config (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(64)  NOT NULL,
    provider     VARCHAR(32)  NOT NULL DEFAULT 'dashscope',
    base_url     VARCHAR(255) DEFAULT NULL,
    api_key      VARCHAR(255) DEFAULT NULL,
    enabled      TINYINT      NOT NULL DEFAULT 1,
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS ai_model;
CREATE TABLE ai_model (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    config_id    BIGINT       NOT NULL,
    model        VARCHAR(128) NOT NULL,
    temperature  DECIMAL(3,2) NOT NULL DEFAULT 0.20,
    is_default   TINYINT      NOT NULL DEFAULT 0,
    enabled      TINYINT      NOT NULL DEFAULT 1,
    created_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS admin_user;
CREATE TABLE admin_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    username    VARCHAR(64)  NOT NULL,
    password    VARCHAR(128) NOT NULL,
    nickname    VARCHAR(64)  DEFAULT NULL,
    role        VARCHAR(32)  NOT NULL DEFAULT 'ADMIN',
    status      TINYINT      NOT NULL DEFAULT 1,
    created_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
);

DROP TABLE IF EXISTS operation_log;
CREATE TABLE operation_log (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    admin_id     BIGINT      DEFAULT NULL,
    admin_name   VARCHAR(64) DEFAULT NULL,
    action       VARCHAR(64) NOT NULL,
    target_type  VARCHAR(32) DEFAULT NULL,
    target_id    BIGINT      DEFAULT NULL,
    detail       TEXT        DEFAULT NULL,
    created_at   DATETIME    DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
);

CREATE INDEX idx_device_brand ON device (brand_id);
CREATE INDEX idx_dpv_device ON device_param_value (device_id);
CREATE INDEX idx_dpv_def ON device_param_value (param_def_id);
CREATE INDEX idx_task_status ON agent_task (status);
CREATE INDEX idx_pending_status ON agent_pending_data (status);
CREATE INDEX idx_ai_model_config ON ai_model (config_id);
CREATE INDEX idx_ai_model_default ON ai_model (is_default);
