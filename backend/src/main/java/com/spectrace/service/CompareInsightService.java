package com.spectrace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.common.BizException;
import com.spectrace.common.RateLimitedException;
import com.spectrace.dto.CompareDeviceVO;
import com.spectrace.dto.CompareGroupVO;
import com.spectrace.dto.CompareInsightVO;
import com.spectrace.dto.CompareParamVO;
import com.spectrace.dto.CompareResultVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * 对比页 AI 差异摘要。
 *
 * <p>按需生成 + 24 小时内存缓存：公开页面必须假设会被刷，缓存既省成本也保证同一组合看到一致结论。
 * 缓存 key 由机型 id 与差异行内容共同决定，参数改了自然失效，不需要额外的失效逻辑。</p>
 *
 * <p>失败一律降级为 {@code failed=true}，不向上抛异常：摘要不是关键路径，不能阻塞对比页。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompareInsightService {

    /** 传给模型的差异行上限，超出按分组顺序截断，避免长 prompt 把成本推高 */
    private static final int MAX_DIFF_LINES = 30;
    private static final int MAX_TOKENS = 400;
    private static final int MAX_POINTS = 3;
    private static final int SUMMARY_LIMIT = 200;
    private static final int POINT_LIMIT = 100;

    private static final long RATE_WINDOW_MS = 60_000L;
    private static final int RATE_LIMIT = 5;
    private static final int MAX_RATE_KEYS = 5_000;

    private static final String SYSTEM_PROMPT = """
            你是手机参数编辑。只依据用户给出的机型与参数差异写摘要，不得引入未提供的规格或外部知识；不确定就不要写。
            输出严格 JSON：{"summary": "一句话总结", "points": ["要点1", "要点2", "要点3"]}
            要求：summary 不超过 80 字；points 2-3 条、每条不超过 40 字，必须带具体数值或明确型号指代；
            不写营销话术，不复述 summary，不评价品牌好坏。
            """;

    private final DeviceService deviceService;
    private final AiChatService aiChatService;
    private final ObjectMapper objectMapper;

    @Value("${spectrace.ai.insight.enabled:true}")
    private boolean enabled;

    @Value("${spectrace.ai.insight.cache-hours:24}")
    private long cacheHours;

    /** key = 机型组合 + 差异内容指纹，value = 已生成的摘要 */
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    /** key = 来源 IP，value = 生成时间窗口 */
    private final Map<String, Deque<Long>> rateBuckets = new ConcurrentHashMap<>();

    /**
     * @param generate false 时只读缓存，用于页面加载时判断入口是否显示
     */
    public CompareInsightVO insight(List<Long> deviceIds, String clientKey, boolean generate) {
        List<Long> ids = normalizeIds(deviceIds);
        CompareInsightVO vo = new CompareInsightVO();
        vo.setAvailable(enabled && aiChatService.available());
        if (!vo.isAvailable()) {
            return vo;
        }
        if (generate) {
            // 限流必须早于缓存查找：生成接口是公开的，命中缓存同样算一次生成请求，
            // 否则同一组合被反复点击时永远走不到限流分支。
            checkRate(clientKey);
        }

        CompareResultVO compared = deviceService.compare(ids);
        List<String> deviceNames = compared.getDevices().stream()
                .map(device -> joinName(device))
                .toList();
        List<String> diffLines = diffLines(compared, deviceNames);
        String key = cacheKey(ids, compared);

        CacheEntry hit = cache.get(key);
        if (hit != null && !expired(hit)) {
            log.debug("对比摘要命中缓存: ids={}", ids);
            return toVO(hit, true);
        }

        if (!generate) {
            return vo;
        }
        if (diffLines.size() < 2) {
            // 参数几乎一致时让模型硬写只会产出空话，直接给确定性结论并缓存
            CacheEntry entry = new CacheEntry(
                    "这几台机型的参数高度接近，差异集中在少数几项，可直接参考上方表格。",
                    List.of(), LocalDateTime.now());
            cache.put(key, entry);
            return toVO(entry, false);
        }

        try {
            String content = aiChatService.complete(SYSTEM_PROMPT, buildPrompt(deviceNames, diffLines), MAX_TOKENS);
            Parsed parsed = parse(content);
            CacheEntry entry = new CacheEntry(parsed.summary(), parsed.points(), LocalDateTime.now());
            cache.put(key, entry);
            cleanupCache();
            log.info("生成对比摘要成功: ids={} 差异行={}", ids, diffLines.size());
            return toVO(entry, false);
        } catch (Exception e) {
            log.error("生成对比摘要失败: ids={} 差异行={}", ids, diffLines.size(), e);
            vo.setFailed(true);
            return vo;
        }
    }

    private List<Long> normalizeIds(List<Long> raw) {
        if (raw == null) {
            throw new BizException("请选择要对比的机型");
        }
        LinkedHashSet<Long> unique = raw.stream()
                .filter(id -> id != null && id > 0)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        if (unique.size() < 2) {
            throw new BizException("至少选择 2 台机型才能生成摘要");
        }
        if (unique.size() > 4) {
            throw new BizException("最多对比 4 台机型");
        }
        return new ArrayList<>(unique);
    }

    private String joinName(CompareDeviceVO device) {
        return StringUtils.hasText(device.getBrandName())
                ? device.getBrandName() + " " + device.getName()
                : device.getName();
    }

    /** 只保留「有实际取值且不相同」的参数行，单位与缺失值一并带给模型。 */
    private List<String> diffLines(CompareResultVO compared, List<String> deviceNames) {
        List<String> lines = new ArrayList<>();
        for (CompareGroupVO group : compared.getGroups()) {
            for (CompareParamVO param : group.getParams()) {
                if (param.isSame() || !hasValue(param.getValues())) {
                    continue;
                }
                List<String> cells = new ArrayList<>();
                for (int index = 0; index < param.getValues().size() && index < deviceNames.size(); index++) {
                    String value = param.getValues().get(index);
                    cells.add(deviceNames.get(index) + "=" + (StringUtils.hasText(value) ? value : "未提供"));
                }
                String unit = StringUtils.hasText(param.getUnit()) ? "(" + param.getUnit() + ")" : "";
                lines.add(param.getParamName() + unit + "：" + String.join(" | ", cells));
                if (lines.size() >= MAX_DIFF_LINES) {
                    return lines;
                }
            }
        }
        return lines;
    }

    private boolean hasValue(List<String> values) {
        return values != null && values.stream()
                .anyMatch(value -> StringUtils.hasText(value) && !"—".equals(value.trim()));
    }

    private String buildPrompt(List<String> deviceNames, List<String> diffLines) {
        String note = diffLines.size() >= MAX_DIFF_LINES ? "（差异项较多，以下为部分差异）" : "";
        return """
                机型：%s
                差异参数%s：
                %s
                任务：概括这几台机型的主要差异与各自侧重，points 至少覆盖差异最明显的两项。
                """.formatted(String.join("；", deviceNames), note, String.join("\n", diffLines));
    }

    private Parsed parse(String content) {
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
                log.warn("对比摘要不是合法 JSON，按纯文本兜底: {}", truncate(text, 100));
            }
        }
        // 模型没按约定输出时仍保留可用结果，比整块报错更有价值
        return new Parsed(truncate(text, SUMMARY_LIMIT), List.of());
    }

    private void checkRate(String clientKey) {
        long now = System.currentTimeMillis();
        Deque<Long> bucket = rateBuckets.computeIfAbsent(clientKey == null ? "unknown" : clientKey,
                key -> new ArrayDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && now - bucket.peekFirst() > RATE_WINDOW_MS) {
                bucket.pollFirst();
            }
            if (bucket.size() >= RATE_LIMIT) {
                log.warn("对比摘要被限流: key={} 窗口内 {} 次", clientKey, bucket.size());
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

    private void cleanupCache() {
        cache.entrySet().removeIf(entry -> expired(entry.getValue()));
    }

    private boolean expired(CacheEntry entry) {
        return entry.generatedAt().plusHours(cacheHours).isBefore(LocalDateTime.now());
    }

    /**
     * 指纹按机型归档参数值，与入参顺序无关：同一组机型换顺序仍命中同一份缓存；
     * 任一参数值变化会改变指纹，天然失效。
     */
    private String cacheKey(List<Long> ids, CompareResultVO compared) {
        Map<Long, StringBuilder> perDevice = new TreeMap<>();
        List<CompareDeviceVO> devices = compared.getDevices();
        for (CompareGroupVO group : compared.getGroups()) {
            for (CompareParamVO param : group.getParams()) {
                for (int index = 0; index < devices.size() && index < param.getValues().size(); index++) {
                    perDevice.computeIfAbsent(devices.get(index).getId(), key -> new StringBuilder())
                            .append(param.getParamKey()).append('=').append(param.getValues().get(index)).append(';');
                }
            }
        }
        String fingerprint = Integer.toHexString(perDevice.toString().hashCode());
        return ids.stream().sorted().map(String::valueOf).collect(Collectors.joining(",")) + "|" + fingerprint;
    }

    private CompareInsightVO toVO(CacheEntry entry, boolean fromCache) {
        CompareInsightVO vo = new CompareInsightVO();
        vo.setAvailable(true);
        vo.setCached(fromCache);
        vo.setSummary(entry.summary());
        vo.setPoints(entry.points());
        vo.setGeneratedAt(entry.generatedAt());
        return vo;
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }

    private record Parsed(String summary, List<String> points) {
    }

    private record CacheEntry(String summary, List<String> points, LocalDateTime generatedAt) {
    }
}
