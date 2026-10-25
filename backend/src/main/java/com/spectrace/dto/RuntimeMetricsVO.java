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
}
