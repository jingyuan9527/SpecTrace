-- ============================================================
-- SpecTrace 开发库种子数据
-- 超级管理员账号由 AdminInitializer 在启动时用 BCrypt 加密写入
-- ============================================================

-- 品牌
INSERT OR IGNORE INTO brand (id, name, logo, sort_order, status) VALUES
 (1, 'Apple',  '/brands/apple.png',   1, 1),
 (2, '小米',   '/brands/xiaomi.png',  2, 1),
 (3, '华为',   '/brands/huawei.png',  3, 1),
 (4, '三星',   '/brands/samsung.png', 4, 1),
 (5, 'OPPO',   '/brands/oppo.png',    5, 1),
 (6, 'vivo',   '/brands/vivo.png',    6, 1);

-- 修正历史种子数据里指向失效 CDN 的 logo（仅当仍是旧链接时更新，不覆盖管理员自定义）
UPDATE brand SET logo = '/brands/apple.png'   WHERE name = 'Apple' AND logo LIKE 'https://cdn.spectrace.dev/%';
UPDATE brand SET logo = '/brands/xiaomi.png'  WHERE name = '小米'  AND logo LIKE 'https://cdn.spectrace.dev/%';
UPDATE brand SET logo = '/brands/huawei.png'  WHERE name = '华为'  AND logo LIKE 'https://cdn.spectrace.dev/%';
UPDATE brand SET logo = '/brands/samsung.png' WHERE name = '三星'  AND logo LIKE 'https://cdn.spectrace.dev/%';
UPDATE brand SET logo = '/brands/oppo.png'    WHERE name = 'OPPO'  AND logo LIKE 'https://cdn.spectrace.dev/%';
UPDATE brand SET logo = '/brands/vivo.png'    WHERE name = 'vivo'  AND logo LIKE 'https://cdn.spectrace.dev/%';

-- 参数分组
INSERT OR IGNORE INTO param_group (id, name, sort_order) VALUES
 (1, '基础信息', 1),
 (2, '屏幕',     2),
 (3, '性能',     3),
 (4, '电池充电', 4),
 (5, '影像',     5),
 (6, '机身',     6),
 (7, '网络',     7),
 (8, '其他',     8);

-- 参数定义
INSERT OR IGNORE INTO param_def (id, param_key, param_name, group_id, param_type, enum_options, unit, sort_order, enabled) VALUES
 (1,  'release_date',        '发布时间',       1, 'date',   NULL, NULL,  1, 1),
 (2,  'launch_price',        '首发价格',       1, 'number', NULL, '元',  2, 1),
 (3,  'screen_size',         '屏幕尺寸',       2, 'number', NULL, '英寸',1, 1),
 (4,  'screen_resolution',   '分辨率',         2, 'text',   NULL, NULL,  2, 1),
 (5,  'screen_refresh_rate', '刷新率',         2, 'number', NULL, 'Hz',  3, 1),
 (6,  'screen_type',         '屏幕类型',       2, 'text',   NULL, NULL,  4, 1),
 (7,  'chipset',             '芯片',           3, 'text',   NULL, NULL,  1, 1),
 (8,  'ram',                 '运行内存',       3, 'text',   NULL, NULL,  2, 1),
 (9,  'storage',             '存储容量',       3, 'text',   NULL, NULL,  3, 1),
 (10, 'os',                  '操作系统',       3, 'text',   NULL, NULL,  4, 1),
 (11, 'battery_capacity',    '电池容量',       4, 'number', NULL, 'mAh', 1, 1),
 (12, 'charging_power',      '有线充电功率',   4, 'number', NULL, 'W',   2, 1),
 (13, 'wireless_charging',   '无线充电',       4, 'text',   NULL, NULL,  3, 1),
 (14, 'rear_camera',         '后置摄像头',     5, 'text',   NULL, NULL,  1, 1),
 (15, 'front_camera',        '前置摄像头',     5, 'text',   NULL, NULL,  2, 1),
 (16, 'video_recording',     '视频录制',       5, 'text',   NULL, NULL,  3, 1),
 (17, 'dimensions',          '机身尺寸',       6, 'text',   NULL, NULL,  1, 1),
 (18, 'weight',              '机身重量',       6, 'number', NULL, 'g',   2, 1),
 (19, 'material',            '机身材质',       6, 'text',   NULL, NULL,  3, 1),
 (20, 'ip_rating',           '防水防尘等级',   6, 'text',   NULL, NULL,  4, 1),
 (21, 'network_5g',          '5G支持',         7, 'text',   NULL, NULL,  1, 1),
 (22, 'sim_slot',            'SIM卡',          7, 'text',   NULL, NULL,  2, 1),
 (23, 'wifi',                'WLAN',           7, 'text',   NULL, NULL,  3, 1),
 (24, 'bluetooth',           '蓝牙',           7, 'text',   NULL, NULL,  4, 1),
 (25, 'uwb',                 'UWB超宽频',      8, 'text',   NULL, NULL,  1, 1),
 (26, 'infrared',            '红外遥控',       8, 'text',   NULL, NULL,  2, 1);

-- 机型
INSERT OR IGNORE INTO device (id, brand_id, name, release_date, thumbnail, description, status) VALUES
 (1, 1, 'iPhone 13', '2021-09-24', '/devices/placeholder.svg',  '苹果 2021 年旗舰机型', 1),
 (2, 1, 'iPhone 14', '2022-09-16', '/devices/placeholder.svg',  '苹果 2022 年旗舰机型', 1),
 (3, 1, 'iPhone 15', '2023-09-22', '/devices/placeholder.svg',  '苹果 2023 年旗舰机型', 1),
 (4, 1, 'iPhone 16', '2024-09-20', '/devices/placeholder.svg',  '苹果 2024 年旗舰机型', 1),
 (5, 2, '小米 14',   '2023-10-26', '/devices/placeholder.svg',  '小米 2023 年数字旗舰', 1),
 (6, 3, '华为 Mate 60 Pro', '2023-08-29', '/devices/placeholder.svg', '华为 2023 年旗舰机型', 1);

-- 产品线（系列）种子与机型归类由 SeriesInitializer 在列迁移之后写入：
-- device.series_id 属于后加列，老库需先补列，脚本阶段直接引用会启动失败

-- 修正历史种子数据里指向失效 CDN 或为空的机型缩略图（保留管理员自定义图片）
UPDATE device SET thumbnail = '/devices/placeholder.svg'
 WHERE thumbnail IS NULL OR thumbnail = '' OR thumbnail LIKE 'https://cdn.spectrace.dev/%';

-- 机型参数值（节选，用于演示对比与历史演变）
INSERT OR IGNORE INTO device_param_value (device_id, param_def_id, param_value) VALUES
 -- iPhone 13
 (1, 1, '2021-09-24'), (1, 3, '6.1'), (1, 5, '60'), (1, 7, 'Apple A15 Bionic'),
 (1, 8, '4GB'), (1, 11, '3240'), (1, 12, '20'), (1, 18, '174'), (1, 20, 'IP68'),
 -- iPhone 14
 (2, 1, '2022-09-16'), (2, 3, '6.1'), (2, 5, '60'), (2, 7, 'Apple A15 Bionic'),
 (2, 8, '6GB'), (2, 11, '3279'), (2, 12, '20'), (2, 18, '172'), (2, 20, 'IP68'),
 -- iPhone 15
 (3, 1, '2023-09-22'), (3, 3, '6.1'), (3, 5, '60'), (3, 7, 'Apple A16 Bionic'),
 (3, 8, '6GB'), (3, 11, '3349'), (3, 12, '20'), (3, 18, '171'), (3, 20, 'IP68'),
 -- iPhone 16
 (4, 1, '2024-09-20'), (4, 3, '6.1'), (4, 5, '60'), (4, 7, 'Apple A18'),
 (4, 8, '8GB'), (4, 11, '3561'), (4, 12, '20'), (4, 18, '170'), (4, 20, 'IP68'),
 -- 小米 14
 (5, 1, '2023-10-26'), (5, 3, '6.36'), (5, 5, '120'), (5, 7, '骁龙 8 Gen 3'),
 (5, 8, '12GB'), (5, 11, '4610'), (5, 12, '90'), (5, 18, '193'), (5, 20, 'IP68'),
 -- 华为 Mate 60 Pro
 (6, 1, '2023-08-29'), (6, 3, '6.82'), (6, 5, '120'), (6, 7, '麒麟 9000S'),
 (6, 8, '12GB'), (6, 11, '5000'), (6, 12, '88'), (6, 18, '225'), (6, 20, 'IP68');

-- 机型存储版本与官方发布价（按机型名关联，避免依赖固定 id；ram 用空串占位以保证唯一约束生效）
INSERT OR IGNORE INTO device_variant (device_id, storage, ram, price, sort_order)
SELECT d.id, v.storage, v.ram, v.price, v.sort_order
FROM device d
JOIN (
    SELECT 'iPhone 13' AS name, '128GB' AS storage, '' AS ram, 5999.00 AS price, 1 AS sort_order
    UNION ALL SELECT 'iPhone 13', '256GB', '', 6799.00, 2
    UNION ALL SELECT 'iPhone 13', '512GB', '', 8399.00, 3
    UNION ALL SELECT 'iPhone 14', '128GB', '', 5999.00, 1
    UNION ALL SELECT 'iPhone 14', '256GB', '', 6899.00, 2
    UNION ALL SELECT 'iPhone 14', '512GB', '', 8699.00, 3
    UNION ALL SELECT 'iPhone 15', '128GB', '', 5999.00, 1
    UNION ALL SELECT 'iPhone 15', '256GB', '', 6999.00, 2
    UNION ALL SELECT 'iPhone 15', '512GB', '', 8999.00, 3
    UNION ALL SELECT 'iPhone 16', '128GB', '', 5999.00, 1
    UNION ALL SELECT 'iPhone 16', '256GB', '', 6999.00, 2
    UNION ALL SELECT 'iPhone 16', '512GB', '', 8999.00, 3
    UNION ALL SELECT 'iPhone 17', '256GB', '', 5999.00, 1
    UNION ALL SELECT 'iPhone 17', '512GB', '', 7999.00, 2
    UNION ALL SELECT '小米 14', '256GB', '8GB', 3999.00, 1
    UNION ALL SELECT '小米 14', '256GB', '12GB', 4299.00, 2
    UNION ALL SELECT '小米 14', '512GB', '16GB', 4599.00, 3
    UNION ALL SELECT '小米 14', '1TB', '16GB', 4999.00, 4
    UNION ALL SELECT '华为 Mate 60 Pro', '256GB', '12GB', 6499.00, 1
    UNION ALL SELECT '华为 Mate 60 Pro', '512GB', '12GB', 6999.00, 2
    UNION ALL SELECT '华为 Mate 60 Pro', '1TB', '12GB', 7999.00, 3
) v ON v.name = d.name;

-- AI 连接与模型配置（API Key 请在后台「AI 配置」中填写）
INSERT OR IGNORE INTO ai_config (id, name, provider, base_url, api_key, enabled) VALUES
 (1, '阿里云百炼', 'dashscope', 'https://dashscope.aliyuncs.com/compatible-mode/v1', '', 1);

INSERT OR IGNORE INTO ai_model (id, config_id, model, temperature, is_default, enabled) VALUES
 (1, 1, 'qwen-max', 0.20, 1, 1);

