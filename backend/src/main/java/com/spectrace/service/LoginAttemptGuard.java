package com.spectrace.service;

import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 登录失败锁定：同一用户名在滑动窗口内失败达到阈值后，锁定至窗口内最早一次失败滑出为止。
 *
 * <p>仅内存实现，重启即清零；多实例部署时各实例独立计数。管理后台账号极少，不需要外部存储。
 */
@Component
public class LoginAttemptGuard {

    static final int MAX_FAILURES = 5;
    static final Duration WINDOW = Duration.ofMinutes(10);

    private final Map<String, Deque<Long>> failures = new ConcurrentHashMap<>();

    /** 距解锁还剩多少秒；0 表示未锁定，可以尝试登录。 */
    public long retryAfterSeconds(String username) {
        Deque<Long> stamps = failures.get(key(username));
        if (stamps == null) {
            return 0;
        }
        prune(stamps);
        if (stamps.size() < MAX_FAILURES) {
            return 0;
        }
        Long oldest = stamps.peekFirst();
        return oldest == null ? 0 : Math.max(1, WINDOW.toSeconds() - (System.currentTimeMillis() - oldest) / 1000);
    }

    public void recordFailure(String username) {
        Deque<Long> stamps = failures.computeIfAbsent(key(username), k -> new ArrayDeque<>());
        synchronized (stamps) {
            prune(stamps);
            stamps.addLast(System.currentTimeMillis());
        }
    }

    public void reset(String username) {
        failures.remove(key(username));
    }

    private void prune(Deque<Long> stamps) {
        long cutoff = System.currentTimeMillis() - WINDOW.toMillis();
        synchronized (stamps) {
            while (!stamps.isEmpty() && stamps.peekFirst() < cutoff) {
                stamps.pollFirst();
            }
        }
    }

    private String key(String username) {
        return username == null ? "" : username.trim().toLowerCase();
    }
}
