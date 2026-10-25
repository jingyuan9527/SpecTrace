package com.spectrace.common;

import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;

/**
 * 进程内事件计数器（T105）：限流 429 等按 key 累加，重启清零——
 * 与「默认单实例」定位一致，只用于观测趋势不用于计费。
 */
@Component
public class MetricsCounter {

    private final Map<String, LongAdder> counters = new ConcurrentHashMap<>();

    public void increment(String key) {
        counters.computeIfAbsent(key, k -> new LongAdder()).increment();
    }

    /** 当前快照，按数值降序便于前端直接渲染。 */
    public Map<String, Long> snapshot() {
        Map<String, Long> snapshot = new LinkedHashMap<>();
        counters.entrySet().stream()
                .sorted((a, b) -> Long.compare(b.getValue().sum(), a.getValue().sum()))
                .forEach(entry -> snapshot.put(entry.getKey(), entry.getValue().sum()));
        return snapshot;
    }
}
