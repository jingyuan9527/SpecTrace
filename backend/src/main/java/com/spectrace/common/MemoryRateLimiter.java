package com.spectrace.common;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 通用单机内存滑动窗口限流器：同一 key 在窗口内的命中次数超过上限即拒绝。
 *
 * <p>仅内存实现，重启即清零、多实例各自计数，与「默认单实例、零外部服务」的部署定位一致。
 * 需要精确全局限流时再换 Redis 计数。</p>
 */
@Component
public class MemoryRateLimiter {

    /** 限流表容量上限，超出时清理窗口外的旧 key，避免长期运行无限增长 */
    private static final int MAX_TRACKED_KEYS = 10_000;
    private static final long IDLE_TTL_MS = Duration.ofHours(1).toMillis();

    private final Map<String, Deque<Long>> hits = new ConcurrentHashMap<>();

    /** 窗口内还有余量则记录本次并返回 true；达到上限返回 false（本次不计入）。 */
    public boolean allow(String key, int limit, Duration window) {
        long now = System.currentTimeMillis();
        Deque<Long> deque = hits.computeIfAbsent(key, k -> new ArrayDeque<>());
        synchronized (deque) {
            long cutoff = now - window.toMillis();
            while (!deque.isEmpty() && deque.peekFirst() < cutoff) {
                deque.pollFirst();
            }
            if (deque.size() >= limit) {
                return false;
            }
            deque.addLast(now);
        }
        cleanupIfNeeded(now);
        return true;
    }

    private void cleanupIfNeeded(long now) {
        if (hits.size() <= MAX_TRACKED_KEYS) {
            return;
        }
        hits.entrySet().removeIf(entry -> {
            Deque<Long> deque = entry.getValue();
            synchronized (deque) {
                Long last = deque.peekLast();
                return last == null || now - last > IDLE_TTL_MS;
            }
        });
    }
}
