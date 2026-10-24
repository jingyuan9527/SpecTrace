package com.spectrace.agent;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 单任务采集会话：剩余预算、工具结果去重缓存、失败域名熔断。
 *
 * <p>消费线程单线程串行，任一时刻最多只有一个任务在执行，静态结构随
 * {@link #begin()}/{@link #end()} 生命周期重置即可；若将来改多线程消费，需改为随任务显式传递。
 */
public final class CrawlSession {

    /** 工具缓存条数上限：防模型异常循环把缓存无限刷大 */
    private static final int MAX_CACHE = 50;

    private static volatile long startMs;
    private static final Map<String, String> TOOL_CACHE = new LinkedHashMap<>();
    private static final Set<String> FAILED_DOMAINS = new java.util.HashSet<>();

    private CrawlSession() {
    }

    public static void begin() {
        startMs = System.currentTimeMillis();
        TOOL_CACHE.clear();
        FAILED_DOMAINS.clear();
    }

    public static void end() {
        startMs = 0;
        TOOL_CACHE.clear();
        FAILED_DOMAINS.clear();
    }

    public static long elapsedMs() {
        return startMs == 0 ? 0 : System.currentTimeMillis() - startMs;
    }

    public static String cached(String key) {
        return TOOL_CACHE.get(key);
    }

    public static void cache(String key, String value) {
        if (TOOL_CACHE.size() >= MAX_CACHE) {
            return;
        }
        TOOL_CACHE.put(key, value);
    }

    /** 该域名是否已在本任务中定性拒绝（401/403），是则后续请求直接跳过。 */
    public static boolean domainFailed(String domain) {
        return domain != null && FAILED_DOMAINS.contains(domain);
    }

    public static void markDomainFailed(String domain) {
        if (domain != null) {
            FAILED_DOMAINS.add(domain);
        }
    }

    public static List<String> failedDomains() {
        return List.copyOf(FAILED_DOMAINS);
    }
}
