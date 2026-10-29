package com.spectrace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.spectrace.common.AiCallFailure;
import com.spectrace.common.BizException;
import com.spectrace.dto.ModelCallDimensionsVO;
import com.spectrace.dto.ModelCallFailureVO;
import com.spectrace.dto.ModelCallMetricsVO;
import com.spectrace.dto.ModelCallModelVO;
import com.spectrace.dto.ModelCallPointVO;
import com.spectrace.dto.ModelCallProviderVO;
import com.spectrace.entity.AiCallLog;
import com.spectrace.entity.AiConfig;
import com.spectrace.entity.AiImageProvider;
import com.spectrace.entity.AiModel;
import com.spectrace.mapper.AiCallLogMapper;
import com.spectrace.mapper.AiConfigMapper;
import com.spectrace.mapper.AiImageProviderMapper;
import com.spectrace.mapper.AiModelMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 模型调用统计的只读聚合。
 *
 * <p>与采集监控同口径：窗口内明细量级有限，直接取回内存聚合，避免在 SQLite 上做
 * 分位函数与多级时间分桶（SQLite 原生不支持百分位）。</p>
 */
@Service
@RequiredArgsConstructor
public class ModelCallMetricsService {

    private static final int MAX_RANGE_DAYS = 90;

    private final AiCallLogMapper callLogMapper;
    private final AiConfigMapper configMapper;
    private final AiModelMapper modelMapper;
    private final AiImageProviderMapper imageProviderMapper;

    /** 筛选项来自当前配置；已删除连接的历史调用仍计入「全部」，但不再提供单独筛选。 */
    public ModelCallDimensionsVO dimensions() {
        ModelCallDimensionsVO vo = new ModelCallDimensionsVO();
        List<ModelCallProviderVO> providers = new ArrayList<>();

        List<AiModel> models = modelMapper.selectList(Wrappers.<AiModel>lambdaQuery()
                .orderByAsc(AiModel::getId));
        Map<Long, List<AiModel>> modelsByConfig = models.stream()
                .filter(model -> model.getConfigId() != null)
                .collect(Collectors.groupingBy(AiModel::getConfigId, LinkedHashMap::new, Collectors.toList()));

        for (AiConfig config : configMapper.selectList(Wrappers.<AiConfig>lambdaQuery()
                .orderByAsc(AiConfig::getId))) {
            ModelCallProviderVO provider = new ModelCallProviderVO();
            provider.setKind(AiCallTarget.KIND_CHAT);
            provider.setId(config.getId());
            provider.setName(config.getName());
            provider.setModels(modelsByConfig.getOrDefault(config.getId(), List.of()).stream()
                    .map(model -> {
                        ModelCallModelVO item = new ModelCallModelVO();
                        item.setId(model.getId());
                        item.setName(model.getModel());
                        return item;
                    })
                    .toList());
            providers.add(provider);
        }

        for (AiImageProvider imageProvider : imageProviderMapper.selectList(Wrappers.<AiImageProvider>lambdaQuery()
                .orderByDesc(AiImageProvider::getIsDefault)
                .orderByAsc(AiImageProvider::getId))) {
            ModelCallProviderVO provider = new ModelCallProviderVO();
            provider.setKind(AiCallTarget.KIND_IMAGE);
            provider.setId(imageProvider.getId());
            provider.setName(imageProvider.getName());
            ModelCallModelVO model = new ModelCallModelVO();
            model.setName(StringUtils.hasText(imageProvider.getModel())
                    ? imageProvider.getModel() : "未配置模型");
            provider.setModels(List.of(model));
            providers.add(provider);
        }

        vo.setProviders(providers);
        return vo;
    }

    public ModelCallMetricsVO metrics(String providerKind, Long providerId, Long modelId,
                                      LocalDateTime start, LocalDateTime end) {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime effectiveEnd = end == null || end.isAfter(now) ? now : end;
        LocalDateTime effectiveStart = start == null ? effectiveEnd.minusHours(24) : start;
        if (!effectiveStart.isBefore(effectiveEnd)) {
            throw new BizException("开始时间必须早于结束时间");
        }
        if (effectiveStart.isBefore(effectiveEnd.minusDays(MAX_RANGE_DAYS))) {
            throw new BizException("时间范围不能超过 " + MAX_RANGE_DAYS + " 天");
        }

        String normalizedKind = normalizeKind(providerKind);
        if (providerId != null && normalizedKind == null) {
            throw new BizException("按供应商筛选时必须指定类型（CHAT / IMAGE）");
        }

        List<AiCallLog> rows = callLogMapper.selectList(Wrappers.<AiCallLog>lambdaQuery()
                .select(AiCallLog::getCreatedAt, AiCallLog::getSuccess, AiCallLog::getLatencyMs,
                        AiCallLog::getErrorType)
                .ge(AiCallLog::getCreatedAt, effectiveStart)
                .le(AiCallLog::getCreatedAt, effectiveEnd)
                .eq(normalizedKind != null, AiCallLog::getProviderKind, normalizedKind)
                .eq(providerId != null, AiCallLog::getProviderId, providerId)
                .eq(modelId != null, AiCallLog::getModelId, modelId)
                .orderByAsc(AiCallLog::getCreatedAt));

        BucketUnit unit = unitFor(effectiveStart, effectiveEnd);
        Map<LocalDateTime, Bucket> buckets = initBuckets(effectiveStart, effectiveEnd, unit);

        List<Long> allLatencies = new ArrayList<>(rows.size());
        Map<String, Long> failureCounts = new LinkedHashMap<>();
        long success = 0;
        long failed = 0;
        for (AiCallLog row : rows) {
            long latency = row.getLatencyMs() == null ? 0 : row.getLatencyMs();
            allLatencies.add(latency);
            LocalDateTime bucketTime = row.getCreatedAt();
            if (bucketTime != null) {
                Bucket bucket = buckets.get(floor(bucketTime, unit));
                if (bucket != null) {
                    bucket.total++;
                    if (Integer.valueOf(1).equals(row.getSuccess())) {
                        bucket.success++;
                    } else {
                        bucket.failed++;
                    }
                    bucket.latencies.add(latency);
                }
            }
            if (Integer.valueOf(1).equals(row.getSuccess())) {
                success++;
            } else {
                failed++;
                String reason = StringUtils.hasText(row.getErrorType())
                        ? row.getErrorType() : AiCallFailure.UNKNOWN.name();
                failureCounts.merge(reason, 1L, Long::sum);
            }
        }

        allLatencies.sort(Long::compareTo);
        ModelCallMetricsVO vo = new ModelCallMetricsVO();
        vo.setStart(effectiveStart);
        vo.setEnd(effectiveEnd);
        vo.setBucketUnit(unit.name());
        vo.setTotal(rows.size());
        vo.setSuccess(success);
        vo.setFailed(failed);
        vo.setSuccessRate(rows.isEmpty() ? null : (double) success / rows.size());
        vo.setAvgMs(average(allLatencies));
        vo.setP50Ms(percentile(allLatencies, 0.50));
        vo.setP95Ms(percentile(allLatencies, 0.95));
        vo.setP99Ms(percentile(allLatencies, 0.99));
        vo.setMaxMs(allLatencies.isEmpty() ? 0 : allLatencies.get(allLatencies.size() - 1));
        vo.setSeries(toPoints(buckets, unit));
        vo.setFailures(toFailures(failureCounts, failed));
        return vo;
    }

    private String normalizeKind(String providerKind) {
        if (!StringUtils.hasText(providerKind)) {
            return null;
        }
        String value = providerKind.trim().toUpperCase(Locale.ROOT);
        if (!AiCallTarget.KIND_CHAT.equals(value) && !AiCallTarget.KIND_IMAGE.equals(value)) {
            throw new BizException("供应商类型仅支持 CHAT / IMAGE");
        }
        return value;
    }

    private BucketUnit unitFor(LocalDateTime start, LocalDateTime end) {
        Duration span = Duration.between(start, end);
        if (span.compareTo(Duration.ofHours(3)) <= 0) {
            return BucketUnit.MINUTE;
        }
        if (span.compareTo(Duration.ofHours(72)) <= 0) {
            return BucketUnit.HOUR;
        }
        return BucketUnit.DAY;
    }

    /** 空闲时间桶补零：折线必须连续，否则「中间没调用」会被误读成没有数据。 */
    private Map<LocalDateTime, Bucket> initBuckets(LocalDateTime start, LocalDateTime end, BucketUnit unit) {
        Map<LocalDateTime, Bucket> buckets = new LinkedHashMap<>();
        LocalDateTime cursor = floor(start, unit);
        LocalDateTime last = floor(end, unit);
        while (!cursor.isAfter(last)) {
            buckets.put(cursor, new Bucket());
            cursor = plus(cursor, unit);
        }
        return buckets;
    }

    private List<ModelCallPointVO> toPoints(Map<LocalDateTime, Bucket> buckets, BucketUnit unit) {
        List<ModelCallPointVO> points = new ArrayList<>(buckets.size());
        buckets.forEach((time, bucket) -> {
            bucket.latencies.sort(Long::compareTo);
            ModelCallPointVO point = new ModelCallPointVO();
            point.setBucket(unit.formatter.format(time));
            point.setTotal(bucket.total);
            point.setSuccess(bucket.success);
            point.setFailed(bucket.failed);
            point.setAvgMs(average(bucket.latencies));
            point.setP95Ms(percentile(bucket.latencies, 0.95));
            point.setMaxMs(bucket.latencies.isEmpty()
                    ? 0 : bucket.latencies.get(bucket.latencies.size() - 1));
            points.add(point);
        });
        return points;
    }

    private List<ModelCallFailureVO> toFailures(Map<String, Long> counts, long failed) {
        return counts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue(Comparator.reverseOrder()))
                .map(entry -> {
                    ModelCallFailureVO vo = new ModelCallFailureVO();
                    vo.setReason(entry.getKey());
                    vo.setLabel(AiCallFailure.labelOf(entry.getKey()));
                    vo.setCount(entry.getValue());
                    vo.setShare(failed == 0 ? null : (double) entry.getValue() / failed);
                    return vo;
                })
                .toList();
    }

    private long average(List<Long> latencies) {
        return latencies.isEmpty()
                ? 0
                : Math.round(latencies.stream().mapToLong(Long::longValue).average().orElse(0));
    }

    private long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.min(Math.floor(p * sorted.size()), sorted.size() - 1);
        return sorted.get(index);
    }

    private LocalDateTime floor(LocalDateTime time, BucketUnit unit) {
        return switch (unit) {
            case MINUTE -> time.truncatedTo(ChronoUnit.MINUTES);
            case HOUR -> time.truncatedTo(ChronoUnit.HOURS);
            case DAY -> time.toLocalDate().atStartOfDay();
        };
    }

    private LocalDateTime plus(LocalDateTime time, BucketUnit unit) {
        return switch (unit) {
            case MINUTE -> time.plusMinutes(1);
            case HOUR -> time.plusHours(1);
            case DAY -> time.plusDays(1);
        };
    }

    private enum BucketUnit {
        MINUTE(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")),
        HOUR(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00")),
        DAY(DateTimeFormatter.ofPattern("yyyy-MM-dd"));

        private final DateTimeFormatter formatter;

        BucketUnit(DateTimeFormatter formatter) {
            this.formatter = formatter;
        }
    }

    private static final class Bucket {
        private long total;
        private long success;
        private long failed;
        private final List<Long> latencies = new ArrayList<>();
    }
}
