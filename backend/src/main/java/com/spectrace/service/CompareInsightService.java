package com.spectrace.service;

import com.spectrace.common.BizException;
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
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * 对比页 AI 差异摘要。
 *
 * <p>按需生成 + 缓存：公开页面必须假设会被刷，缓存既省成本也保证同一组合看到一致结论。
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

    private static final String SYSTEM_PROMPT = """
            你是手机参数编辑。只依据用户给出的机型与参数差异写摘要，不得引入未提供的规格或外部知识；不确定就不要写。
            输出严格 JSON：{"summary": "一句话总结", "points": ["要点1", "要点2", "要点3"]}
            要求：summary 不超过 80 字；points 2-3 条、每条不超过 40 字，必须带具体数值或明确型号指代；
            不写营销话术，不复述 summary，不评价品牌好坏。
            """;

    private final DeviceService deviceService;
    private final AiChatService aiChatService;
    private final AiInsightSupport insightSupport;

    @Value("${spectrace.ai.insight.enabled:true}")
    private boolean enabled;

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
            insightSupport.checkRate(clientKey);
        }

        CompareResultVO compared = deviceService.compare(ids);
        List<String> deviceNames = compared.getDevices().stream()
                .map(this::joinName)
                .toList();
        List<String> diffLines = diffLines(compared, deviceNames);
        String key = "compare:" + cacheKey(ids, compared);

        AiInsightSupport.Entry hit = insightSupport.cached(key);
        if (hit != null) {
            log.debug("对比摘要命中缓存: ids={}", ids);
            return toVO(hit, true);
        }

        if (!generate) {
            return vo;
        }
        if (diffLines.size() < 2) {
            // 参数几乎一致时让模型硬写只会产出空话，直接给确定性结论并缓存
            AiInsightSupport.Entry entry = new AiInsightSupport.Entry(
                    "这几台机型的参数高度接近，差异集中在少数几项，可直接参考上方表格。",
                    List.of(), LocalDateTime.now());
            insightSupport.store(key, entry);
            return toVO(entry, false);
        }

        try {
            String content = aiChatService.complete(SYSTEM_PROMPT, buildPrompt(deviceNames, diffLines), MAX_TOKENS);
            AiInsightSupport.Parsed parsed = insightSupport.parse(content);
            AiInsightSupport.Entry entry = new AiInsightSupport.Entry(
                    parsed.summary(), parsed.points(), LocalDateTime.now());
            insightSupport.store(key, entry);
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

    private CompareInsightVO toVO(AiInsightSupport.Entry entry, boolean fromCache) {
        CompareInsightVO vo = new CompareInsightVO();
        vo.setAvailable(true);
        vo.setCached(fromCache);
        vo.setSummary(entry.summary());
        vo.setPoints(entry.points());
        vo.setGeneratedAt(entry.generatedAt());
        return vo;
    }
}
