package com.spectrace.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.spectrace.common.BizException;
import com.spectrace.common.PageResult;
import com.spectrace.common.RateLimitedException;
import com.spectrace.dto.AiImageGenerateRequest;
import com.spectrace.dto.AiImageGenerateVO;
import com.spectrace.dto.AiImageStatusVO;
import com.spectrace.dto.EvolutionDeviceVO;
import com.spectrace.dto.EvolutionParamVO;
import com.spectrace.dto.EvolutionPointVO;
import com.spectrace.dto.EvolutionVO;
import com.spectrace.entity.AiImageJob;
import com.spectrace.entity.AiImagePrompt;
import com.spectrace.entity.AiImageProvider;
import com.spectrace.mapper.AiImageJobMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.LocalDate;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * AI 绘图：把「系列 + 机型范围 + 参数」渲染进提示词模板，调用 OpenAI 兼容的
 * {@code /images/generations}，成品图落盘并记录一次绘图历史。
 *
 * <p>刻意不接收前端传来的提示词正文：公开接口如果允许任意 prompt，等于把绘图额度直接交给匿名访客。
 * 访客只能选择模板与数据范围，正文与变量值都在服务端生成。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AiImageGenerationService {

    /** 单次提示词上限：美术模型对超长提示词没有收益，只增加成本与不确定性 */
    private static final int MAX_PROMPT_CHARS = 6000;
    private static final int MAX_TITLE_CHARS = 60;
    /** 与趋势页参数上限保持一致，超过后海报版面也读不清 */
    private static final int MAX_PARAMS = 8;
    private static final int MAX_DEVICE_NAME_CHARS = 60;
    private static final long RATE_WINDOW_MS = 60_000L;
    private static final int MAX_RATE_KEYS = 5_000;

    private final AiImageProviderService providerService;
    private final AiImagePromptService promptService;
    private final EvolutionService evolutionService;
    private final AiImageJobMapper jobMapper;
    private final ObjectMapper objectMapper;

    @Value("${spectrace.ai.image.enabled:true}")
    private boolean enabled;

    @Value("${spectrace.ai.image.rate-limit-per-minute:3}")
    private int rateLimit;

    @Value("${spectrace.ai.image.timeout-seconds:120}")
    private long timeoutSeconds;

    @Value("${spectrace.upload-dir:uploads}")
    private String uploadDir;

    @Value("${spectrace.upload.max-bytes:8388608}")
    private long maxBytes;

    private final HttpClient httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .build();

    /** key = 来源 IP，value = 请求时间窗口；绘图成本高于文案，限流也比 AI 摘要更严 */
    private final Map<String, Deque<Long>> rateBuckets = new ConcurrentHashMap<>();

    /** 前端据此决定是否展示「AI 海报」入口，未配置时不给访客点了才失败的机会。 */
    public AiImageStatusVO status() {
        AiImageStatusVO vo = new AiImageStatusVO();
        vo.setEnabled(enabled);
        if (!enabled) {
            vo.setAvailable(false);
            vo.setReason("AI 绘图已关闭");
            return vo;
        }
        boolean providerReady = providerService.configured();
        boolean promptReady = promptService.hasEnabled();
        vo.setAvailable(providerReady && promptReady);
        if (!providerReady) {
            vo.setReason("尚未配置可用的绘图供应商");
        } else if (!promptReady) {
            vo.setReason("尚未配置可用的提示词模板");
        }
        return vo;
    }

    public List<AiImagePrompt> promptOptions() {
        return promptService.listEnabled();
    }

    public AiImageGenerateVO generate(AiImageGenerateRequest request, String clientKey) {
        if (!enabled) {
            throw new BizException("AI 绘图未开启");
        }
        if (request == null || request.getSeriesId() == null) {
            throw new BizException("缺少系列参数");
        }
        // 先限流再取数：即便后续数据校验失败，也不能让匿名请求无成本地反复打进来
        checkRate(clientKey);

        AiImageProvider provider = providerService.resolve();
        EvolutionVO evolution = evolutionService.evolution(request.getSeriesId(), request.getParamDefIds());
        PromptContext context = buildContext(evolution, request);
        AiImagePrompt prompt = promptService.resolve(request.getPromptId());
        String promptText = promptService.render(prompt.getContent(), context.variables());
        if (!StringUtils.hasText(promptText)) {
            throw new BizException("提示词模板渲染结果为空，请检查模板正文");
        }

        long startedAt = System.currentTimeMillis();
        log.info("AI 绘图开始: seriesId={} provider={} model={} promptId={} devices={} params={}",
                request.getSeriesId(), provider.getName(), provider.getModel(), prompt.getId(),
                context.devices().size(), context.paramNames().size());
        try {
            byte[] bytes = requestImage(provider, promptText);
            String ext = detectImageType(bytes);
            if (ext == null) {
                throw new IllegalStateException("绘图服务返回的不是支持的图片类型（JPG/PNG/WebP）");
            }
            String filename = "ai-image-" + UUID.randomUUID().toString().replace("-", "") + "." + ext;
            Path dir = Path.of(uploadDir);
            Files.createDirectories(dir);
            Files.write(dir.resolve(filename), bytes);
            String imageUrl = "/uploads/" + filename;

            long cost = System.currentTimeMillis() - startedAt;
            AiImageJob job = buildJob(provider, prompt, promptText, context);
            job.setStatus("SUCCESS");
            job.setImageUrl(imageUrl);
            job.setDurationMs(cost);
            insertJobQuietly(job);

            AiImageGenerateVO vo = new AiImageGenerateVO();
            vo.setJobId(job.getId());
            vo.setImageUrl(imageUrl);
            vo.setPromptId(prompt.getId());
            vo.setPromptName(prompt.getName());
            vo.setPromptText(promptText);
            vo.setModel(provider.getModel());
            vo.setDurationMs(cost);
            log.info("AI 绘图成功: seriesId={} imageUrl={} size={}B cost={}ms",
                    request.getSeriesId(), imageUrl, bytes.length, cost);
            return vo;
        } catch (Exception e) {
            long cost = System.currentTimeMillis() - startedAt;
            log.error("AI 绘图失败: seriesId={} provider={} model={} promptId={} cost={}ms",
                    request.getSeriesId(), provider.getName(), provider.getModel(), prompt.getId(), cost, e);
            AiImageJob job = buildJob(provider, prompt, promptText, context);
            job.setStatus("FAILED");
            job.setErrorMsg(truncate(e.getMessage(), 500));
            job.setDurationMs(cost);
            insertJobQuietly(job);
            throw new BizException("绘图失败，请稍后再试");
        }
    }

    public PageResult<AiImageJob> history(long current, long size, String status) {
        var query = Wrappers.<AiImageJob>lambdaQuery()
                .orderByDesc(AiImageJob::getCreatedAt)
                .orderByDesc(AiImageJob::getId);
        if (StringUtils.hasText(status)) {
            query.eq(AiImageJob::getStatus, status);
        }
        IPage<AiImageJob> page = jobMapper.selectPage(new Page<>(current, Math.min(size, 100)), query);
        return PageResult.of(page);
    }

    /** 清理历史：行与文件一起删，否则 uploads 会被历史图慢慢占满。 */
    public int deleteJobs(List<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        List<Long> distinct = ids.stream().filter(id -> id != null).distinct().toList();
        if (distinct.isEmpty()) {
            return 0;
        }
        List<AiImageJob> jobs = jobMapper.selectBatchIds(distinct);
        int deleted = jobMapper.delete(Wrappers.<AiImageJob>lambdaQuery().in(AiImageJob::getId, distinct));
        for (AiImageJob job : jobs) {
            deleteImageFile(job.getImageUrl());
        }
        log.info("AI 绘图历史已清理: 请求 {} 条，实际删除 {} 条", distinct.size(), deleted);
        return deleted;
    }

    // ---------------- 上下文与模板 ----------------

    private record PromptContext(List<EvolutionDeviceVO> devices, List<String> paramNames,
                                 Map<String, String> variables) {
    }

    /**
     * 把世代回顾结果换算成模板变量。
     *
     * <p>机型范围允许在系列内裁剪（前端只导出当前可见世代）；参数点与机型列表按下标对齐，
     * 裁剪后必须同步过滤，否则「窄机型的图」会带出整条系列的取值。</p>
     */
    private PromptContext buildContext(EvolutionVO evolution, AiImageGenerateRequest request) {
        List<EvolutionDeviceVO> allDevices = evolution.getDevices() == null ? List.of() : evolution.getDevices();
        if (allDevices.isEmpty()) {
            throw new BizException("该系列暂无可展示机型");
        }
        Set<Long> selectedIds = request.getDeviceIds() == null ? Set.of()
                : request.getDeviceIds().stream().filter(id -> id != null).collect(Collectors.toSet());
        List<EvolutionDeviceVO> devices = selectedIds.isEmpty() ? allDevices
                : allDevices.stream().filter(device -> selectedIds.contains(device.getId())).toList();
        if (devices.isEmpty()) {
            throw new BizException("所选机型不在该系列内");
        }

        List<String> dateTexts = devices.stream()
                .map(EvolutionDeviceVO::getReleaseDate)
                .filter(java.util.Objects::nonNull)
                .map(date -> String.valueOf(date.getYear()))
                .distinct()
                .toList();
        String yearRange = dateTexts.isEmpty() ? "年份未收录"
                : dateTexts.size() == 1 ? dateTexts.get(0) + " 年"
                : dateTexts.get(0) + "-" + dateTexts.get(dateTexts.size() - 1);

        String deviceLines = devices.stream()
                .map(device -> "- " + truncate(device.getName(), MAX_DEVICE_NAME_CHARS) + dateSuffix(device.getReleaseDate()))
                .collect(Collectors.joining("\n"));

        List<String> paramNames = new ArrayList<>();
        List<String> paramLines = new ArrayList<>();
        List<EvolutionParamVO> params = evolution.getParams() == null ? List.of() : evolution.getParams();
        for (EvolutionParamVO param : params) {
            if (paramLines.size() >= MAX_PARAMS) {
                break;
            }
            List<EvolutionPointVO> aligned = alignPoints(param.getPoints(), allDevices, selectedIds);
            String line = paramLine(param, aligned);
            if (line == null) {
                continue;
            }
            paramNames.add(param.getParamName());
            paramLines.add("- " + line);
        }
        if (paramLines.isEmpty()) {
            throw new BizException("所选范围内没有可用的参数取值");
        }

        String series = StringUtils.hasText(evolution.getSeriesName()) ? evolution.getSeriesName() : "该系列";
        String title = sanitizeTitle(request.getTitle(), series + " 历代参数进化");
        Map<String, String> variables = new LinkedHashMap<>();
        variables.put("brand", evolution.getBrandName() == null ? "" : evolution.getBrandName());
        variables.put("series", series);
        variables.put("count", String.valueOf(devices.size()));
        variables.put("devices", deviceLines);
        variables.put("yearRange", yearRange);
        variables.put("paramLines", String.join("\n", paramLines));
        variables.put("title", title);
        return new PromptContext(devices, paramNames, variables);
    }

    /** 参数点与机型列表同序；按选中的机型 ID 过滤，保持原顺序。 */
    private List<EvolutionPointVO> alignPoints(List<EvolutionPointVO> points, List<EvolutionDeviceVO> allDevices,
                                               Set<Long> selectedIds) {
        List<EvolutionPointVO> result = new ArrayList<>();
        if (points == null) {
            return result;
        }
        for (int i = 0; i < points.size() && i < allDevices.size(); i++) {
            if (selectedIds.isEmpty() || selectedIds.contains(allDevices.get(i).getId())) {
                result.add(points.get(i));
            }
        }
        return result;
    }

    /** 首末非空取值拼成「6.1 英寸 → 6.3 英寸」，只做展示不做涨跌幅计算。 */
    private String paramLine(EvolutionParamVO param, List<EvolutionPointVO> points) {
        String first = null;
        String last = null;
        for (EvolutionPointVO point : points) {
            if (point == null || !StringUtils.hasText(point.getRaw())) {
                continue;
            }
            if (first == null) {
                first = point.getRaw();
            }
            last = point.getRaw();
        }
        if (first == null || last == null) {
            return null;
        }
        String unit = param.getUnit() == null ? "" : param.getUnit();
        String head = withUnit(first, unit);
        String tail = withUnit(last, unit);
        return param.getParamName() + "：" + (head.equals(tail) ? head : head + " → " + tail);
    }

    private String withUnit(String raw, String unit) {
        if (!StringUtils.hasText(unit) || raw.contains(unit)) {
            return raw;
        }
        return raw + unit;
    }

    private String dateSuffix(LocalDate date) {
        return date == null ? "" : "（" + date.getYear() + " 年 " + date.getMonthValue() + " 月）";
    }

    private String sanitizeTitle(String title, String fallback) {
        String value = StringUtils.hasText(title) ? title : fallback;
        // 换行会破坏模板排版，标题只保留单行
        value = value.replaceAll("[\\r\\n]+", " ").trim();
        return truncate(value, MAX_TITLE_CHARS);
    }

    // ---------------- 供应商调用 ----------------

    /**
     * 调用 OpenAI 兼容的 {@code /images/generations}。
     *
     * <p>不发送 {@code response_format}：gpt-image-1 明确拒绝该字段，而 dall-e-3 默认返回 URL，
     * 两种返回都在这里统一消化，兼容成本最低。</p>
     */
    private byte[] requestImage(AiImageProvider provider, String promptText) throws Exception {
        String url = provider.getBaseUrl().replaceAll("/+$", "") + "/images/generations";
        ObjectNode body = objectMapper.createObjectNode();
        body.put("model", provider.getModel());
        body.put("prompt", truncate(promptText, MAX_PROMPT_CHARS));
        body.put("n", 1);
        if (StringUtils.hasText(provider.getSize())) {
            body.put("size", provider.getSize());
        }
        if (StringUtils.hasText(provider.getQuality())) {
            body.put("quality", provider.getQuality());
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(
                        objectMapper.writeValueAsString(body), StandardCharsets.UTF_8));
        if (StringUtils.hasText(provider.getApiKey())) {
            builder.header("Authorization", "Bearer " + provider.getApiKey());
        }

        HttpResponse<String> response = httpClient.send(builder.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (response.statusCode() >= 400) {
            log.warn("绘图接口返回失败: url={} model={} status={} body={}",
                    url, provider.getModel(), response.statusCode(), truncate(response.body(), 300));
            throw new IllegalStateException("HTTP " + response.statusCode() + " " + truncate(response.body(), 200));
        }

        JsonNode root = objectMapper.readTree(response.body());
        JsonNode first = root.path("data").path(0);
        String base64 = first.path("b64_json").asText("");
        if (StringUtils.hasText(base64)) {
            return decodeBase64(base64);
        }
        String imageUrl = first.path("url").asText("");
        if (StringUtils.hasText(imageUrl)) {
            return downloadImage(imageUrl);
        }
        String error = root.path("error").path("message").asText("");
        throw new IllegalStateException("绘图接口未返回图片" + (StringUtils.hasText(error) ? "：" + error : ""));
    }

    private byte[] decodeBase64(String raw) {
        // 部分中转会带 data:image/png;base64, 前缀
        int comma = raw.indexOf(',');
        String payload = raw.startsWith("data:") && comma > 0 ? raw.substring(comma + 1) : raw;
        return Base64.getDecoder().decode(payload);
    }

    private byte[] downloadImage(String imageUrl) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(imageUrl))
                .timeout(Duration.ofSeconds(timeoutSeconds))
                .header("User-Agent", "Mozilla/5.0 (compatible; SpecTrace/1.0)")
                .GET()
                .build();
        HttpResponse<byte[]> response = httpClient.send(request, HttpResponse.BodyHandlers.ofByteArray());
        if (response.statusCode() >= 400) {
            throw new IllegalStateException("图片下载失败 HTTP " + response.statusCode());
        }
        byte[] bytes = response.body();
        if (bytes.length > maxBytes) {
            throw new IllegalStateException("生成图片超过大小上限 " + (maxBytes / 1024 / 1024) + "MB");
        }
        return bytes;
    }

    /** 文件头判定真实类型，不信任供应商返回的 URL 后缀。 */
    private String detectImageType(byte[] head) {
        if (head.length >= 3 && (head[0] & 0xFF) == 0xFF && (head[1] & 0xFF) == 0xD8 && (head[2] & 0xFF) == 0xFF) {
            return "jpg";
        }
        if (head.length >= 8 && head[0] == (byte) 0x89 && head[1] == 'P' && head[2] == 'N' && head[3] == 'G') {
            return "png";
        }
        if (head.length >= 12 && head[0] == 'R' && head[1] == 'I' && head[2] == 'F' && head[3] == 'F'
                && head[8] == 'W' && head[9] == 'E' && head[10] == 'B' && head[11] == 'P') {
            return "webp";
        }
        return null;
    }

    // ---------------- 历史与限流 ----------------

    private AiImageJob buildJob(AiImageProvider provider, AiImagePrompt prompt, String promptText,
                                PromptContext context) {
        AiImageJob job = new AiImageJob();
        job.setProviderId(provider.getId());
        job.setProviderName(provider.getName());
        job.setModel(provider.getModel());
        job.setPromptId(prompt.getId());
        job.setPromptName(prompt.getName());
        job.setPromptText(promptText);
        job.setDeviceNames(truncate(context.devices().stream().map(EvolutionDeviceVO::getName)
                .collect(Collectors.joining("、")), 500));
        job.setParamNames(truncate(String.join("、", context.paramNames()), 500));
        return job;
    }

    /** 历史写入失败只降级为应用日志：用户拿到的图不该因为统计表写失败而丢。 */
    private void insertJobQuietly(AiImageJob job) {
        try {
            jobMapper.insert(job);
        } catch (Exception e) {
            log.error("AI 绘图历史写入失败: provider={} status={}", job.getProviderName(), job.getStatus(), e);
        }
    }

    private void checkRate(String clientKey) {
        long now = System.currentTimeMillis();
        Deque<Long> bucket = rateBuckets.computeIfAbsent(clientKey == null ? "unknown" : clientKey,
                key -> new ArrayDeque<>());
        synchronized (bucket) {
            while (!bucket.isEmpty() && now - bucket.peekFirst() > RATE_WINDOW_MS) {
                bucket.pollFirst();
            }
            if (bucket.size() >= rateLimit) {
                log.warn("AI 绘图被限流: key={} 窗口内 {} 次，上限 {}", clientKey, bucket.size(), rateLimit);
                throw new RateLimitedException("绘图请求过于频繁，请稍后再试");
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

    private void deleteImageFile(String imageUrl) {
        if (!StringUtils.hasText(imageUrl) || !imageUrl.startsWith("/uploads/")) {
            return;
        }
        String filename = imageUrl.substring("/uploads/".length());
        if (filename.contains("/") || filename.contains("\\") || filename.contains("..")) {
            log.warn("跳过可疑的绘图文件路径: {}", imageUrl);
            return;
        }
        try {
            boolean removed = Files.deleteIfExists(Path.of(uploadDir, filename));
            log.debug("清理绘图文件: file={} removed={}", filename, removed);
        } catch (Exception e) {
            log.warn("清理绘图文件失败: file={}", filename, e);
        }
    }

    private String truncate(String text, int max) {
        if (text == null) {
            return "";
        }
        String trimmed = text.trim();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max);
    }
}
