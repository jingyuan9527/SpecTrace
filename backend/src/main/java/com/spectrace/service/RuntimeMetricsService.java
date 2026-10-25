package com.spectrace.service;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.spectrace.common.MetricsCounter;
import com.spectrace.config.CacheConfig;
import com.spectrace.dto.CacheStatVO;
import com.spectrace.dto.RuntimeMetricsVO;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * 技术运行时指标（T105）：Caffeine 命中率与限流拒绝计数，只读快照。
 */
@Service
@RequiredArgsConstructor
public class RuntimeMetricsService {

    private final CacheManager cacheManager;
    private final MetricsCounter metricsCounter;

    public RuntimeMetricsVO snapshot() {
        RuntimeMetricsVO vo = new RuntimeMetricsVO();
        vo.setCaches(cacheStats());
        vo.setRateLimitRejected(metricsCounter.snapshot());
        return vo;
    }

    private List<CacheStatVO> cacheStats() {
        List<CacheStatVO> result = new ArrayList<>();
        for (String name : List.of(CacheConfig.BRANDS, CacheConfig.PARAM_META, CacheConfig.SERIES_LIST, CacheConfig.DEVICE_DETAIL)) {
            CacheStatVO stat = new CacheStatVO();
            stat.setName(name);
            Cache cache = cacheManager.getCache(name);
            if (cache instanceof org.springframework.cache.caffeine.CaffeineCache caffeineCache) {
                CacheStats stats = caffeineCache.getNativeCache().stats();
                stat.setHitCount(stats.hitCount());
                stat.setMissCount(stats.missCount());
                long total = stats.hitCount() + stats.missCount();
                stat.setHitRate(total == 0 ? null : (double) stats.hitCount() / total);
                stat.setEstimatedSize(caffeineCache.getNativeCache().estimatedSize());
                stat.setEvictionCount(stats.evictionCount());
            }
            result.add(stat);
        }
        return result;
    }
}
