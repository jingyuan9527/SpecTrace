package com.spectrace.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * 技术运行时指标（T105 起）：缓存命中率与限流拒绝计数，后续任务继续扩充。
 */
@Data
public class RuntimeMetricsVO {

    private List<CacheStatVO> caches;

    /** 限流拒绝计数（key 归一化后的接口路径），重启清零 */
    private Map<String, Long> rateLimitRejected;

    // ---- T107 运行时卡片 ----

    /** SQLite 主库文件路径 */
    private String sqlitePath;

    private long sqliteSizeBytes;

    /** 数据目录所在盘（字节） */
    private long diskTotalBytes;

    private long diskFreeBytes;

    /** JVM 堆使用 / 上限（字节） */
    private long heapUsedBytes;

    private long heapMaxBytes;

    /** 活跃线程数 */
    private int threads;

    /** 最近一次备份耗时（毫秒），重启后为 0 */
    private long lastBackupDurationMs;

    /** 产品图待下载数量 */
    private long imagePending;
}
