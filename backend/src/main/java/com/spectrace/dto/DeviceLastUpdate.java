package com.spectrace.dto;

import lombok.Data;

/**
 * 机型参数最近一次更新时间（陈旧度判定用，T87）。
 */
@Data
public class DeviceLastUpdate {

    private Long deviceId;

    /** MAX(updated_at) 原始字符串，由服务层解析比较（SQLite 存储格式含 T 分隔符） */
    private String lastUpdated;
}
