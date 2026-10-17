-- ============================================================
-- SpecTrace 开发库种子数据
-- 超级管理员账号由 AdminInitializer 在启动时用 BCrypt 加密写入
-- ============================================================

-- 品牌
INSERT INTO brand (id, name, logo, sort_order, status) VALUES
 (1, 'Apple',  'https://cdn.spectrace.dev/brand/apple.svg',  1, 1),
 (2, '小米',   'https://cdn.spectrace.dev/brand/xiaomi.svg', 2, 1),
 (3, '华为',   'https://cdn.spectrace.dev/brand/huawei.svg', 3, 1),
 (4, '三星',   'https://cdn.spectrace.dev/brand/samsung.svg',4, 1),
 (5, 'OPPO',   'https://cdn.spectrace.dev/brand/oppo.svg',   5, 1),
 (6, 'vivo',   'https://cdn.spectrace.dev/brand/vivo.svg',   6, 1);

-- 参数分组
INSERT INTO param_group (id, name, sort_order) VALUES
 (1, '基础信息', 1),
 (2, '屏幕',     2),
 (3, '性能',     3),
 (4, '电池充电', 4),
 (5, '影像',     5),
 (6, '机身',     6),
 (7, '网络',     7),
 (8, '其他',     8);

-- 参数定义
INSERT INTO param_def (id, param_key, param_name, group_id, param_type, enum_options, unit, sort_order, enabled) VALUES
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
INSERT INTO device (id, brand_id, name, release_date, thumbnail, description, status) VALUES
 (1, 1, 'iPhone 13', '2021-09-24', 'https://cdn.spectrace.dev/device/iphone13.jpg',  '苹果 2021 年旗舰机型', 1),
 (2, 1, 'iPhone 14', '2022-09-16', 'https://cdn.spectrace.dev/device/iphone14.jpg',  '苹果 2022 年旗舰机型', 1),
 (3, 1, 'iPhone 15', '2023-09-22', 'https://cdn.spectrace.dev/device/iphone15.jpg',  '苹果 2023 年旗舰机型', 1),
 (4, 1, 'iPhone 16', '2024-09-20', 'https://cdn.spectrace.dev/device/iphone16.jpg',  '苹果 2024 年旗舰机型', 1),
 (5, 2, '小米 14',   '2023-10-26', 'https://cdn.spectrace.dev/device/mi14.jpg',      '小米 2023 年数字旗舰', 1),
 (6, 3, '华为 Mate 60 Pro', '2023-08-29', 'https://cdn.spectrace.dev/device/mate60pro.jpg', '华为 2023 年旗舰机型', 1);

-- 机型参数值（节选，用于演示对比与历史演变）
INSERT INTO device_param_value (device_id, param_def_id, param_value) VALUES
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

-- AI 连接与模型配置（API Key 请在后台「AI 配置」中填写）
INSERT INTO ai_config (id, name, provider, base_url, api_key, enabled) VALUES
 (1, '阿里云百炼', 'dashscope', 'https://dashscope.aliyuncs.com/compatible-mode/v1', '', 1);

INSERT INTO ai_model (id, config_id, model, temperature, is_default, enabled) VALUES
 (1, 1, 'qwen-max', 0.20, 1, 1);

