package com.spectrace.service;

import com.spectrace.dto.EvolutionDeviceVO;
import com.spectrace.dto.EvolutionParamVO;
import com.spectrace.dto.EvolutionPointVO;
import com.spectrace.dto.EvolutionVO;
import com.spectrace.dto.SeriesInsightVO;
import com.spectrace.dto.SocialCopyVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 世代回顾 AI 文案：把一个系列在所选参数上的变化写成一段带趋势判断的回顾。
 *
 * <p>只喂「当前可见世代 + 所选参数」的原始取值，模型不接触外部知识。
 * 缓存 key 含可见机型与取值，换区间或参数改动都会自然失效。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SeriesInsightService {

    /** 与世代回顾的参数上限一致，行数不会超过它 */
    private static final int MAX_LINES = 8;
    /** 世代过多时每行只保留首尾若干代，否则 prompt 会被单行撑长 */
    private static final int MAX_CELLS = 6;
    private static final int MAX_TOKENS = 500;

    private static final String SYSTEM_PROMPT = """
            你是手机参数编辑，正在回顾一条产品线各代机型的变化。
            只依据用户给出的机型序列与参数取值，不得引入未提供的规格、发布时间或外部知识。
            输出严格 JSON：{"summary": "整体走向", "points": ["要点1", "要点2", "要点3"]}
            要求：summary 不超过 120 字，概括这条产品线在所选参数上的整体走向；
            points 2-3 条、每条不超过 50 字，允许给出「升级明显 / 提升有限 / 略有退步」这类判断，
            但每一条都必须由给出的数值支撑，并带上具体数值或机型指代；
            不写购买建议，不评价品牌好坏，不复述 summary。
            """;

    private final EvolutionService evolutionService;
    private final AiChatService aiChatService;
    private final AiInsightSupport insightSupport;

    @Value("${spectrace.ai.insight.enabled:true}")
    private boolean enabled;

    /**
     * @param deviceIds   可见世代；为空表示整条系列
     * @param generate    false 时只读缓存，用于决定页面入口是否展示
     */
    public SeriesInsightVO insight(Long seriesId,
                                   List<Long> deviceIds,
                                   List<Long> paramDefIds,
                                   String clientKey,
                                   boolean generate) {
        SeriesInsightVO vo = new SeriesInsightVO();
        vo.setAvailable(enabled && aiChatService.available());
        if (!vo.isAvailable()) {
            return vo;
        }
        if (generate) {
            // 与对比摘要一致：限流早于缓存查找，命中缓存同样算一次生成
            insightSupport.checkRate(clientKey);
        }

        EvolutionVO evolution = evolutionService.evolution(seriesId, paramDefIds);
        List<String> lines = trendLines(evolution, deviceIds);
        String key = cacheKey(seriesId, evolution, deviceIds, paramDefIds, lines);

        AiInsightSupport.Entry hit = insightSupport.cached(key);
        if (hit != null) {
            log.debug("世代回顾命中缓存: seriesId={} 行数={}", seriesId, lines.size());
            return toVO(hit, true);
        }

        if (!generate) {
            return vo;
        }
        if (lines.isEmpty()) {
            // 没有可比较的数值参数时，让模型硬写只会产出空话
            AiInsightSupport.Entry entry = new AiInsightSupport.Entry(
                    "当前区间内没有可比较的数值参数，勾选屏幕尺寸、电池容量等数值型参数后可生成回顾。",
                    List.of(), LocalDateTime.now());
            insightSupport.store(key, entry);
            return toVO(entry, false);
        }

        try {
            String content = aiChatService.complete(SYSTEM_PROMPT, buildPrompt(evolution, lines), MAX_TOKENS);
            AiInsightSupport.Parsed parsed = insightSupport.parse(content);
            AiInsightSupport.Entry entry = new AiInsightSupport.Entry(
                    parsed.summary(), parsed.points(), LocalDateTime.now());
            insightSupport.store(key, entry);
            log.info("生成世代回顾成功: seriesId={} 机型={} 参数行={}", seriesId,
                    evolution.getDevices().size(), lines.size());
            return toVO(entry, false);
        } catch (Exception e) {
            log.error("生成世代回顾失败: seriesId={} 参数行={}", seriesId, lines.size(), e);
            vo.setFailed(true);
            return vo;
        }
    }

    /** 每个数值参数一行，只保留有取值的代际；文本参数无法谈趋势，直接跳过。 */
    private List<String> trendLines(EvolutionVO evolution, List<Long> deviceIds) {
        Map<Long, String> nameOf = evolution.getDevices().stream()
                .collect(Collectors.toMap(EvolutionDeviceVO::getId, EvolutionDeviceVO::getName, (a, b) -> a));
        Set<Long> allowed = deviceIds == null || deviceIds.isEmpty() ? null : new HashSet<>(deviceIds);

        List<String> lines = new ArrayList<>();
        for (EvolutionParamVO param : evolution.getParams()) {
            if (!param.isNumeric() || param.getPoints() == null) {
                continue;
            }
            List<String> cells = new ArrayList<>();
            for (EvolutionPointVO point : param.getPoints()) {
                if (allowed != null && !allowed.contains(point.getDeviceId())) {
                    continue;
                }
                if (!StringUtils.hasText(point.getRaw())) {
                    continue;
                }
                cells.add(nameOf.getOrDefault(point.getDeviceId(), "机型" + point.getDeviceId()) + "=" + point.getRaw());
            }
            if (cells.size() < 2) {
                continue;
            }
            String unit = StringUtils.hasText(param.getUnit()) ? "(" + param.getUnit() + ")" : "";
            lines.add(param.getParamName() + unit + "：" + compress(cells));
            if (lines.size() >= MAX_LINES) {
                break;
            }
        }
        return lines;
    }

    /** 代际过多时保留首尾：趋势判断依赖端点，中间几代对结论影响最小。 */
    private String compress(List<String> cells) {
        if (cells.size() <= MAX_CELLS) {
            return String.join(" | ", cells);
        }
        int half = MAX_CELLS / 2;
        List<String> head = cells.subList(0, half);
        List<String> tail = cells.subList(cells.size() - half, cells.size());
        return String.join(" | ", head) + " | … | " + String.join(" | ", tail);
    }

    private String buildPrompt(EvolutionVO evolution, List<String> lines) {
        String names = evolution.getDevices().stream()
                .map(EvolutionDeviceVO::getName)
                .collect(Collectors.joining(" → "));
        return """
                系列：%s · %s
                机型：%s
                参数变化：
                %s
                任务：概括这条产品线在这些参数上的整体走向与最明显的变化。
                """.formatted(evolution.getBrandName(), evolution.getSeriesName(), names, String.join("\n", lines));
    }

    private String cacheKey(Long seriesId,
                            EvolutionVO evolution,
                            List<Long> deviceIds,
                            List<Long> paramDefIds,
                            List<String> lines) {
        String devices = deviceIds == null || deviceIds.isEmpty()
                ? evolution.getDevices().stream().map(d -> String.valueOf(d.getId())).sorted().collect(Collectors.joining(","))
                : deviceIds.stream().filter(id -> id != null).sorted().map(String::valueOf).collect(Collectors.joining(","));
        String params = paramDefIds == null ? "" : paramDefIds.stream()
                .filter(id -> id != null).sorted().map(String::valueOf).collect(Collectors.joining(","));
        return "series:" + seriesId + "|" + devices + "|" + params + "|"
                + Integer.toHexString(String.join("\n", lines).hashCode());
    }

    private SeriesInsightVO toVO(AiInsightSupport.Entry entry, boolean fromCache) {
        SeriesInsightVO vo = new SeriesInsightVO();
        vo.setAvailable(true);
        vo.setCached(fromCache);
        vo.setSummary(entry.summary());
        vo.setPoints(entry.points());
        vo.setGeneratedAt(entry.generatedAt());
        return vo;
    }

    /** 支持的社媒风格：key 同时用于缓存隔离，新增风格需同步前端选项 */
    private static final Set<String> COPY_STYLES = Set.of("xiaohongshu", "wechat");

    private static final String XHS_PROMPT = """
            你是数码自媒体作者，正在为小红书写一篇产品线历代对比的种草帖。
            只依据用户给出的机型序列与参数取值，不得引入未提供的规格或外部知识。
            输出严格 JSON：{"title": "标题", "summary": "正文", "points": ["要点"], "tags": ["#话题"]}
            要求：title 不超过 20 字，带具体数字或对比钩子，不用夸张医美词汇；
            summary 150-250 字，口语化短句，可用少量 emoji，逐条点出最有传播力的数据变化；
            points 1-3 条，每条不超过 40 字且必须由给出的数值支撑；
            tags 3-5 个以 # 开头的话题标签。
            """;

    private static final String WECHAT_PROMPT = """
            你是数码栏目编辑，正在为公众号写一段产品线历代对比的点评。
            只依据用户给出的机型序列与参数取值，不得引入未提供的规格或外部知识。
            输出严格 JSON：{"title": "标题", "summary": "正文", "points": ["要点"], "tags": []}
            要求：title 不超过 30 字，克制、信息量优先；
            summary 180-300 字，段落式行文，先总述再分点，判断必须由给出的数值支撑；
            points 2-3 条，每条不超过 50 字；tags 输出空数组。
            """;

    /**
     * 社媒文案：与站内回顾同源数据，但按平台风格输出可直接发布的标题/正文/话题。
     * 缓存 key 以 copy:style 前缀隔离——同一区间的小红书稿与公众号稿互不覆盖。
     */
    public SocialCopyVO socialCopy(Long seriesId,
                                   List<Long> deviceIds,
                                   List<Long> paramDefIds,
                                   String style,
                                   String clientKey,
                                   boolean generate) {
        SocialCopyVO vo = new SocialCopyVO();
        String normalized = style == null ? "" : style.trim();
        if (!COPY_STYLES.contains(normalized)) {
            normalized = "xiaohongshu";
        }
        vo.setStyle(normalized);
        vo.setAvailable(enabled && aiChatService.available());
        if (!vo.isAvailable()) {
            return vo;
        }
        if (generate) {
            // 与回顾/对比摘要共用同一份每分钟限额：对用户是同一种「AI 生成」动作
            insightSupport.checkRate(clientKey);
        }

        EvolutionVO evolution = evolutionService.evolution(seriesId, paramDefIds);
        List<String> lines = trendLines(evolution, deviceIds);
        String key = "copy:" + normalized + "|" + cacheKey(seriesId, evolution, deviceIds, paramDefIds, lines);

        AiInsightSupport.Entry hit = insightSupport.cached(key);
        if (hit != null && hit.title() != null) {
            log.debug("社媒文案命中缓存: seriesId={} style={}", seriesId, normalized);
            return toCopyVO(normalized, hit, true);
        }

        if (!generate) {
            return vo;
        }
        if (lines.isEmpty()) {
            AiInsightSupport.Entry entry = new AiInsightSupport.Entry(
                    "当前区间内没有可比较的数值参数，勾选屏幕尺寸、电池容量等数值型参数后可生成文案。",
                    List.of(), LocalDateTime.now());
            insightSupport.store(key, entry);
            return toCopyVO(normalized, entry, false);
        }

        try {
            String content = aiChatService.complete(
                    "xiaohongshu".equals(normalized) ? XHS_PROMPT : WECHAT_PROMPT,
                    buildPrompt(evolution, lines), 600);
            AiInsightSupport.ParsedCopy parsed = insightSupport.parseCopy(content);
            AiInsightSupport.Entry entry = new AiInsightSupport.Entry(
                    parsed.summary(), parsed.points(), LocalDateTime.now(), parsed.title(), parsed.tags());
            insightSupport.store(key, entry);
            log.info("生成社媒文案成功: seriesId={} style={} 参数行={}", seriesId, normalized, lines.size());
            return toCopyVO(normalized, entry, false);
        } catch (Exception e) {
            log.error("生成社媒文案失败: seriesId={} style={}", seriesId, normalized, e);
            vo.setFailed(true);
            return vo;
        }
    }

    private SocialCopyVO toCopyVO(String style, AiInsightSupport.Entry entry, boolean fromCache) {
        SocialCopyVO vo = new SocialCopyVO();
        vo.setAvailable(true);
        vo.setCached(fromCache);
        vo.setStyle(style);
        vo.setTitle(entry.title());
        vo.setSummary(entry.summary());
        vo.setPoints(entry.points());
        vo.setTags(entry.tags() == null ? List.of() : entry.tags());
        vo.setGeneratedAt(entry.generatedAt());
        return vo;
    }
}
