-- ============================================================
-- SpecTrace 手机参数对比平台 - MySQL 生产库建表脚本
-- MySQL 8.0+ / utf8mb4
--
-- 动态参数设计：品牌表 / 参数定义表 / 机型主表 / 机型参数值表 分离，
-- 新增参数只需向 param_def 插入记录，无需 DDL 改表。
-- ============================================================

CREATE DATABASE IF NOT EXISTS spectrace
    DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE spectrace;

-- 1. 品牌表
DROP TABLE IF EXISTS brand;
CREATE TABLE brand (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(64)  NOT NULL COMMENT '品牌名称',
    logo        VARCHAR(512) DEFAULT NULL COMMENT '品牌 Logo 地址',
    sort_order  INT          NOT NULL DEFAULT 0 COMMENT '排序',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 停用',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    PRIMARY KEY (id),
    KEY idx_brand_sort (sort_order)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '品牌表';

-- 2. 参数分组表
DROP TABLE IF EXISTS param_group;
CREATE TABLE param_group (
    id          BIGINT      NOT NULL AUTO_INCREMENT,
    name        VARCHAR(64) NOT NULL COMMENT '分组名称',
    sort_order  INT         NOT NULL DEFAULT 0,
    created_at  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted     TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '参数分组表';

-- 3. 参数定义表
DROP TABLE IF EXISTS param_def;
CREATE TABLE param_def (
    id            BIGINT      NOT NULL AUTO_INCREMENT,
    param_key     VARCHAR(64) NOT NULL COMMENT '参数稳定标识，Agent Schema 与对比逻辑的键',
    param_name    VARCHAR(64) NOT NULL COMMENT '参数名称',
    group_id      BIGINT      NOT NULL COMMENT '所属分组',
    param_type    VARCHAR(16) NOT NULL DEFAULT 'text' COMMENT 'text/number/image/date/select',
    enum_options  JSON        DEFAULT NULL COMMENT 'select 类型的枚举选项',
    unit          VARCHAR(32) DEFAULT NULL COMMENT '单位',
    sort_order    INT         NOT NULL DEFAULT 0,
    enabled       TINYINT     NOT NULL DEFAULT 1,
    created_at    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted       TINYINT     NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_param_key (param_key),
    KEY idx_param_group (group_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '参数定义表(动态字段)';

-- 4. 机型主表
DROP TABLE IF EXISTS device;
CREATE TABLE device (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    brand_id     BIGINT       NOT NULL,
    name         VARCHAR(128) NOT NULL COMMENT '机型名称',
    release_date DATE         DEFAULT NULL COMMENT '发布日期',
    thumbnail    VARCHAR(512) DEFAULT NULL COMMENT '缩略图地址',
    description  VARCHAR(1024) DEFAULT NULL COMMENT '简介',
    status       TINYINT      NOT NULL DEFAULT 1 COMMENT '1 上架 0 下架',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_device_brand (brand_id),
    KEY idx_device_release (release_date),
    KEY idx_device_name (name)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '机型主表';

-- 5. 机型参数值表（value 统一存字符串，前端按类型转换）
DROP TABLE IF EXISTS device_param_value;
CREATE TABLE device_param_value (
    id            BIGINT   NOT NULL AUTO_INCREMENT,
    device_id     BIGINT   NOT NULL,
    param_def_id  BIGINT   NOT NULL,
    param_value   TEXT     DEFAULT NULL COMMENT '统一以字符串存储',
    created_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_device_param (device_id, param_def_id),
    KEY idx_dpv_def (param_def_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '机型参数值表';

-- 6. Agent 抓取任务表
DROP TABLE IF EXISTS agent_task;
CREATE TABLE agent_task (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    model_name   VARCHAR(128) NOT NULL COMMENT '目标机型名称',
    brand_name   VARCHAR(64)  DEFAULT NULL COMMENT '品牌名称(可选，减少歧义)',
    ai_model_id  BIGINT       DEFAULT NULL COMMENT '指定使用的 AI 模型(ai_model.id)，空则用默认模型',
    status       VARCHAR(24)  NOT NULL DEFAULT 'QUEUED'
                 COMMENT 'QUEUED/RUNNING/PENDING_REVIEW/APPROVED/DISCARDED/FAILED',
    source_url   VARCHAR(512) DEFAULT NULL COMMENT '主要来源链接',
    result       JSON         DEFAULT NULL COMMENT 'Agent 回传结构化结果',
    error_msg    TEXT         DEFAULT NULL,
    duration_ms  BIGINT       DEFAULT NULL,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_task_status (status),
    KEY idx_task_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Agent 抓取任务表';

-- 7. 待审核数据表
DROP TABLE IF EXISTS agent_pending_data;
CREATE TABLE agent_pending_data (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    task_id      BIGINT       DEFAULT NULL COMMENT '来源任务',
    model_name   VARCHAR(128) NOT NULL,
    brand_name   VARCHAR(64)  DEFAULT NULL,
    params_json  JSON         DEFAULT NULL COMMENT 'paramKey -> value',
    source_url   VARCHAR(512) DEFAULT NULL COMMENT '来源链接，供人工复核',
    status       VARCHAR(24)  NOT NULL DEFAULT 'PENDING_REVIEW'
                 COMMENT 'PENDING_REVIEW/APPROVED/DISCARDED',
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_pending_status (status),
    KEY idx_pending_task (task_id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'Agent 抓取待审核数据表';

-- 7.5 AI 供应商连接表（一个连接下可配置多个模型）
DROP TABLE IF EXISTS ai_config;
CREATE TABLE ai_config (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    name         VARCHAR(64)  NOT NULL COMMENT '连接名称，如 阿里云百炼 / 本地 Ollama',
    provider     VARCHAR(32)  NOT NULL DEFAULT 'dashscope'
                 COMMENT 'dashscope/openai/deepseek/moonshot/ollama/...',
    base_url     VARCHAR(255) DEFAULT NULL COMMENT '接口地址(OpenAI 兼容 base_url)',
    api_key      VARCHAR(255) DEFAULT NULL COMMENT 'API Key(建议加密存储)',
    enabled      TINYINT      NOT NULL DEFAULT 1,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 供应商连接表';

-- 7.6 AI 模型表（连接下可拉取/导入多个模型，其中一个为默认）
DROP TABLE IF EXISTS ai_model;
CREATE TABLE ai_model (
    id           BIGINT       NOT NULL AUTO_INCREMENT,
    config_id    BIGINT       NOT NULL COMMENT '所属连接',
    model        VARCHAR(128) NOT NULL COMMENT '模型名称，如 qwen-max',
    temperature  DECIMAL(3,2) NOT NULL DEFAULT 0.20,
    is_default   TINYINT      NOT NULL DEFAULT 0 COMMENT '是否默认模型',
    enabled      TINYINT      NOT NULL DEFAULT 1,
    created_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted      TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    KEY idx_ai_model_config (config_id),
    KEY idx_ai_model_default (is_default)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = 'AI 模型表';

-- 初始化一条默认连接与模型，API Key 请在后台填写
INSERT INTO ai_config (name, provider, base_url, api_key, enabled)
VALUES ('阿里云百炼', 'dashscope',
        'https://dashscope.aliyuncs.com/compatible-mode/v1', '', 1);
INSERT INTO ai_model (config_id, model, temperature, is_default, enabled)
VALUES (1, 'qwen-max', 0.20, 1, 1);

-- 8. 管理员账号表
DROP TABLE IF EXISTS admin_user;
CREATE TABLE admin_user (
    id          BIGINT       NOT NULL AUTO_INCREMENT,
    username    VARCHAR(64)  NOT NULL,
    password    VARCHAR(128) NOT NULL COMMENT 'BCrypt 加密',
    nickname    VARCHAR(64)  DEFAULT NULL,
    role        VARCHAR(32)  NOT NULL DEFAULT 'ADMIN' COMMENT 'SUPER_ADMIN/ADMIN',
    status      TINYINT      NOT NULL DEFAULT 1 COMMENT '1 启用 0 禁用',
    created_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    deleted     TINYINT      NOT NULL DEFAULT 0,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '管理员账号表';

-- 9. 操作日志表
DROP TABLE IF EXISTS operation_log;
CREATE TABLE operation_log (
    id           BIGINT      NOT NULL AUTO_INCREMENT,
    admin_id     BIGINT      DEFAULT NULL,
    admin_name   VARCHAR(64) DEFAULT NULL,
    action       VARCHAR(64) NOT NULL COMMENT '操作类型',
    target_type  VARCHAR(32) DEFAULT NULL COMMENT '目标类型',
    target_id    BIGINT      DEFAULT NULL,
    detail       TEXT        DEFAULT NULL,
    created_at   DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_log_target (target_type, target_id),
    KEY idx_log_created (created_at)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '操作日志表';

-- 初始化超级管理员：admin / admin123（BCrypt 密文，上线后请立即修改）
INSERT INTO admin_user (username, password, nickname, role, status)
VALUES ('admin', '$2a$10$x2W9aA7yKyhDtbeP9xYeGuXT0FHYo3tWE6iggrSJBr3jKZIFriNzu', '超级管理员', 'SUPER_ADMIN', 1);
