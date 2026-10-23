package com.spectrace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.common.RateLimitedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

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

    /** 两类能力共用一张缓存表，靠 key 前缀区分；过期时间统一取配置项。 */
    private final Map<String, Entry> cache = new ConcurrentHashMap<>();

    /** key = 来源 IP，value = 生成时间窗口 */
    private final Map<String, Deque<Long>> rateBuckets = new ConcurrentHashMap<>();

    public record Parsed(String summary, List<String> points) {
    }

    public record Entry(String summary, List<String> points, LocalDateTime generatedAt) {
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
