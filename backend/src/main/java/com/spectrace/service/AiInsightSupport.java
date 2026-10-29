package com.spectrace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.common.RateLimitedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * AI 摘要类能力的公共护栏：生成限流、结果缓存与模型输出解析。
 *
 * <p>对比差异摘要与世代回顾共用同一套阈值与解析规则。分开各写一份时，
 * 限流次数、缓存时长或 JSON 兜底策略很容易一处改了另一处没改。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AiInsightSupport {

    private static final long RATE_WINDOW_MS = 60_000L;
    private static final int RATE_LIMIT = 5;
    private static final int MAX_RATE_KEYS = 5_000;
    private static final int SUMMARY_LIMIT = 200;
    private static final int POINT_LIMIT = 100;
    private static final int MAX_POINTS = 3;

    private final ObjectMapper objectMapper;

    @Value("${spectrace.ai.insight.cache-hours:24}")
    private long cacheHours;

    /** 全站日配额（对比摘要 / 世代回顾 / 社媒文案共享），0 表示不限制 */
    @Value("${spectrace.ai.insight.daily-quota:100}")
    private int dailyQuota;

    /** 两类能力共用一张缓存表，靠 key 前缀区分；过期时间统一取配置项。 */
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    /** key = 来源 IP，value = 生成时间窗口 */
    private final Map<String, Deque<Long>> rateBuckets = new ConcurrentHashMap<>();

    /** 日配额计数只含真实模型调用（成本所在），缓存命中与失败调用不消耗 */
    private final AtomicInteger dailyUsed = new AtomicInteger();
    private volatile LocalDate dailyWindow = LocalDate.now();

    public record Parsed(String summary, List<String> points) {
    }

    /** 社媒文案解析结果：title/tags 为风格化扩展，纯文本兜底时为 null/空 */
    public record ParsedCopy(String title, String summary, List<String> points, List<String> tags) {
    }

    public record Entry(String summary, List<String> points, LocalDateTime generatedAt, String title, List<String> tags) {

        /** 站内摘要类结果不携带标题与话题标签 */
        public Entry(String summary, List<String> points, LocalDateTime generatedAt) {
            this(summary, points, generatedAt, null, null);
        }
    }

    /** 命中未过期缓存返回条目，未命中或已过期返回 null。 */
    public Entry cached(String key) {
        Entry entry = cache.get(key);
        if (entry == null) {
            return null;
        }
        if (expired(entry)) {
            cache.remove(key);
            return null;
        }
        return entry;
    }

    public void store(String key, Entry entry) {
        cache.put(key, entry);
        // 过期清理放在写路径，读路径不为维护付代价
        cache.entrySet().removeIf(item -> expired(item.getValue()));
    }

    /**
     * 公开接口按来源限流。
     *
     * <p>调用方必须在查缓存之前调用：命中缓存同样算一次生成请求，
     * 否则同一组合被反复点击时永远走不到限流分支。</p>
     */
    public void checkRate(String clientKey) {
        long now = System.currentTimeMillis();
        Deque<Long> bucket = rateBuckets.computeIfAbsent(clientKey == null ? "unknown" : clientKey,
                key -> new ArrayDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && now - bucket.peekFirst() > RATE_WINDOW_MS) {
                bucket.pollFirst();
            }
            if (bucket.size() >= RATE_LIMIT) {
                log.warn("AI 摘要被限流: key={} 窗口内 {} 次", clientKey, bucket.size());
                throw new RateLimitedException("生成过于频繁，请稍后再试");
            }
            bucket.addLast(now);
        }
        if (rateBuckets.size() > MAX_RATE_KEYS) {
            rateBuckets.entrySet().removeIf(entry -> {
                Deque<Long> times = entry.getValue();
                synchronized (times) {
                    return times.isEmpty() || now - times.peekLast() > RATE_WINDOW_MS;
                }
            });
        }
    }

    /**
     * 全站日配额闸门：只拦真实模型调用，必须在缓存未命中、即将请求模型之前调用，
     * 且必须放在调用方 try-catch 之外——否则会被「失败降级」吞掉，丢失 429 语义。
     * 检查与计数之间存在并发窗口，突发流量可能小幅越过上限，对成本护栏而言可接受。
     */
    public void checkDailyQuota() {
        if (dailyQuota <= 0) {
            return;
        }
        rollDailyWindow();
        if (dailyUsed.get() >= dailyQuota) {
            log.warn("AI 文本日配额已用尽: quota={} used={}", dailyQuota, dailyUsed.get());
            throw new RateLimitedException("今日 AI 生成次数已达上限，请明天再来");
        }
    }

    /** 模型调用成功产生实际成本后计数。 */
    public void recordDailyUsage() {
        if (dailyQuota <= 0) {
            return;
        }
        rollDailyWindow();
        int used = dailyUsed.incrementAndGet();
        if (used >= dailyQuota) {
            log.warn("AI 文本日配额已用尽: {}/{}", used, dailyQuota);
        } else {
            log.info("AI 文本日配额消耗: {}/{}", used, dailyQuota);
        }
    }

    /** 按自然日翻窗（服务器时区），跨天后计数清零。 */
    private void rollDailyWindow() {
        LocalDate today = LocalDate.now();
        if (dailyWindow.equals(today)) {
            return;
        }
        synchronized (this) {
            if (!dailyWindow.equals(today)) {
                dailyWindow = today;
                dailyUsed.set(0);
            }
        }
    }

    /** 优先按约定的 JSON 解析，模型没按格式输出时退化为纯文本摘要，避免整块不可用。 */
    public Parsed parse(String content) {
        String text = content == null ? "" : content.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                JsonNode node = objectMapper.readTree(text.substring(start, end + 1));
                String summary = truncate(node.path("summary").asText(""), SUMMARY_LIMIT);
                List<String> points = new ArrayList<>();
                for (JsonNode item : node.path("points")) {
                    String point = truncate(item.asText(""), POINT_LIMIT);
                    if (StringUtils.hasText(point)) {
                        points.add(point);
                    }
                    if (points.size() >= MAX_POINTS) {
                        break;
                    }
                }
                if (StringUtils.hasText(summary)) {
                    return new Parsed(summary, points);
                }
            } catch (Exception e) {
                log.warn("AI 摘要不是合法 JSON，按纯文本兜底: {}", truncate(text, 100));
            }
        }
        return new Parsed(truncate(text, SUMMARY_LIMIT), List.of());
    }

    /**
     * 社媒文案解析：约定输出 {"title","summary","points","tags"}，解析失败退化为纯文本正文。
     * tags 只保留以 # 开头的话题项，模型输出裸词时自动补 #。
     */
    public ParsedCopy parseCopy(String content) {
        String text = content == null ? "" : content.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start >= 0 && end > start) {
            try {
                JsonNode node = objectMapper.readTree(text.substring(start, end + 1));
                String title = truncate(node.path("title").asText(""), 60);
                String summary = truncate(node.path("summary").asText(""), 600);
                List<String> points = stringsFrom(node.path("points"), 3, POINT_LIMIT);
                List<String> tags = stringsFrom(node.path("tags"), 5, 20);
                if (StringUtils.hasText(summary)) {
                    return new ParsedCopy(
                            StringUtils.hasText(title) ? title : null,
                            summary, points,
                            tags.stream().map(tag -> tag.startsWith("#") ? tag : "#" + tag).toList());
                }
            } catch (Exception e) {
                log.warn("AI 社媒文案不是合法 JSON，按纯文本兜底: {}", truncate(text, 100));
            }
        }
        return new ParsedCopy(null, truncate(text, 600), List.of(), List.of());
    }

    private List<String> stringsFrom(JsonNode array, int max, int limit) {
        List<String> result = new ArrayList<>();
        if (array == null || !array.isArray()) {
            return result;
        }
        for (JsonNode item : array) {
            String value = truncate(item.asText(""), limit);
            if (StringUtils.hasText(value)) {
                result.add(value);
            }
            if (result.size() >= max) {
                break;
            }
        }
        return result;
    }

    private boolean expired(Entry entry) {
        return entry.generatedAt().plusHours(cacheHours).isBefore(LocalDateTime.now());
    }

    private static String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
