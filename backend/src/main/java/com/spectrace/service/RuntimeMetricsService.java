package com.spectrace.service;

import com.github.benmanes.caffeine.cache.stats.CacheStats;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.spectrace.common.MetricsCounter;
import com.spectrace.config.CacheConfig;
import com.spectrace.dto.CacheStatVO;
import com.spectrace.dto.RuntimeMetricsVO;
import com.spectrace.entity.Device;
import com.spectrace.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

/**
 * 技术运行时指标（T105 起）：缓存命中率、限流拒绝与 SQLite/磁盘/JVM/备份运行时（T107）。
 * 磁盘水位告警见 {@link MetricsAlertService}，这里只做展示。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class RuntimeMetricsService {

    private final CacheManager cacheManager;
    private final MetricsCounter metricsCounter;
    private final DeviceMapper deviceMapper;
    private final BackupService backupService;

    /** 与数据源同一个库文件路径，解析 jdbc:sqlite: 前缀 */
    @Value("${spring.datasource.url}")
    private String datasourceUrl;

    public RuntimeMetricsVO snapshot() {
        RuntimeMetricsVO vo = new RuntimeMetricsVO();
        vo.setCaches(cacheStats());
        vo.setRateLimitRejected(metricsCounter.snapshot());
        fillRuntime(vo);
        return vo;
    }

    private void fillRuntime(RuntimeMetricsVO vo) {
        try {
            Path dbPath = Paths.get(datasourceUrl.replaceFirst("^jdbc:sqlite:", "").split("\\?")[0]);
            vo.setSqlitePath(dbPath.toAbsolutePath().toString());
            vo.setSqliteSizeBytes(Files.exists(dbPath) ? Files.size(dbPath) : 0);
            Path dataDir = dbPath.toAbsolutePath().getParent();
            if (dataDir != null) {
                var store = Files.getFileStore(dataDir);
                vo.setDiskTotalBytes(store.getTotalSpace());
                vo.setDiskFreeBytes(store.getUsableSpace());
            }
        } catch (Exception e) {
            log.warn("SQLite/磁盘指标读取失败", e);
        }
        Runtime jvm = Runtime.getRuntime();
        vo.setHeapUsedBytes(jvm.totalMemory() - jvm.freeMemory());
        vo.setHeapMaxBytes(jvm.maxMemory());
        vo.setThreads(ManagementFactory.getThreadMXBean().getThreadCount());
        vo.setLastBackupDurationMs(backupService.getLastBackupDurationMs());
        try {
            vo.setImagePending(deviceMapper.selectCount(Wrappers.<Device>lambdaQuery()
                    .isNotNull(Device::getThumbnailPendingUrl)));
        } catch (Exception e) {
            log.warn("图片队列统计失败", e);
        }
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
