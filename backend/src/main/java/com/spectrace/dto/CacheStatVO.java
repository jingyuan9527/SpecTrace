package com.spectrace.dto;

import lombok.Data;

/**
 * 单个 Caffeine 缓存的命中统计（T105）。
 */
@Data
public class CacheStatVO {

    private String name;

    private long hitCount;

    private long missCount;

    /** 命中率 0~1，无请求时为 null */
    private Double hitRate;

    private long estimatedSize;

    private long evictionCount;
}
