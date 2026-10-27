package com.spectrace.service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.spectrace.mapper.DeviceViewDailyMapper;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 浏览量内存聚合：请求线程只改内存，定时批量落库。
 *
 * <p>存在理由：详情与对比是全站最热的读路径，原先每次访问都在请求线程里逐条 UPSERT。
 * SQLite 是单写者，写越多越容易让公开读接口排队等锁——统计是旁路数据，没有资格占用主链路。
 * 代价是进程异常退出会丢失最近一批计数（分钟级），统计口径可接受。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class DeviceViewAggregator {

    /** 同一来源对同一机型的重复访问只计一次：刷新页面不该反复抬高 PV */
    private static final Duration DEDUPE_WINDOW = Duration.ofMinutes(10);

    /** 待落库缓冲上限：极端流量下宁可丢计数，也不让内存无限增长 */
    private static final int MAX_PENDING_KEYS = 50_000;

    private final DeviceViewDailyMapper viewMapper;

    private final ConcurrentHashMap<Long, Integer> pending = new ConcurrentHashMap<>();
    private final Cache<String, Boolean> seen = Caffeine.newBuilder()
            .maximumSize(100_000)
            .expireAfterWrite(DEDUPE_WINDOW)
            .build();

    public void record(String clientIp, Collection<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return;
        }
        String ip = clientIp == null || clientIp.isBlank() ? "unknown" : clientIp;
        for (Long deviceId : deviceIds) {
            if (deviceId == null) {
                continue;
            }
            String key = ip + ':' + deviceId;
            if (seen.getIfPresent(key) != null) {
                continue;
            }
            if (pending.size() >= MAX_PENDING_KEYS && !pending.containsKey(deviceId)) {
                log.warn("浏览量缓冲区已满，丢弃本次计数: deviceId={} pending={}", deviceId, pending.size());
                continue;
            }
            seen.put(key, Boolean.TRUE);
            pending.merge(deviceId, 1, Integer::sum);
        }
    }

    @Scheduled(fixedDelayString = "${spectrace.view.flush-ms:15000}")
    public void scheduledFlush() {
        flush();
    }

    /** 停机前兜底落库：避免重启把最后一批计数全部带走。 */
    @PreDestroy
    public void flushOnShutdown() {
        flush();
    }

    /**
     * 把内存计数写入库。单条失败放回缓冲区等下一轮，不重试到死循环：
     * 落库本身已是低频批量操作，失败通常是磁盘/锁问题，交给下一轮更稳。
     */
    public void flush() {
        if (pending.isEmpty()) {
            return;
        }
        Map<Long, Integer> batch = new LinkedHashMap<>();
        for (Long deviceId : pending.keySet()) {
            Integer delta = pending.remove(deviceId);
            if (delta != null && delta > 0) {
                batch.put(deviceId, delta);
            }
        }
        if (batch.isEmpty()) {
            return;
        }
        String today = LocalDate.now().toString();
        int failed = 0;
        for (Map.Entry<Long, Integer> entry : batch.entrySet()) {
            try {
                viewMapper.upsertIncrementBy(entry.getKey(), today, entry.getValue());
            } catch (Exception e) {
                failed++;
                pending.merge(entry.getKey(), entry.getValue(), Integer::sum);
                log.warn("浏览量落库失败，已放回待重试: deviceId={} delta={}", entry.getKey(), entry.getValue(), e);
            }
        }
        int total = batch.values().stream().mapToInt(Integer::intValue).sum();
        if (failed > 0) {
            log.warn("浏览量落库部分失败: 机型 {} 个，失败 {} 个，计数 {} 次", batch.size(), failed, total);
        } else {
            log.info("浏览量落库完成: 机型 {} 个，计数 {} 次", batch.size(), total);
        }
    }
}
