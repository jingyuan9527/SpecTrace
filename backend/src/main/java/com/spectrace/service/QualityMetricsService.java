package com.spectrace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.spectrace.dto.FeedbackParamStat;
import com.spectrace.dto.QualityMetricsVO;
import com.spectrace.entity.Feedback;
import com.spectrace.mapper.DeviceParamValueMapper;
import com.spectrace.mapper.FeedbackMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据质量观测（T106）：陈旧度分布、反馈处理时效与被报错参数排行，只读聚合。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class QualityMetricsService {

    private static final int STALE_DAYS = 180;
    /** SQLite DATETIME 读回字符串的比较格式 */
    private static final DateTimeFormatter STORAGE = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private final DeviceParamValueMapper valueMapper;
    private final FeedbackMapper feedbackMapper;

    public QualityMetricsVO overview() {
        QualityMetricsVO vo = new QualityMetricsVO();
        vo.setStaleThresholdDays(STALE_DAYS);
        try {
            vo.setStaleDevices(valueMapper.countStaleDevices(
                    LocalDateTime.now().minusDays(STALE_DAYS).format(STORAGE)));

            List<Feedback> open = feedbackMapper.selectList(Wrappers.<Feedback>lambdaQuery()
                    .eq(Feedback::getStatus, "OPEN"));
            vo.setFeedbackOpen(open.size());
            vo.setFeedbackAvgAgeHours(open.isEmpty() ? null
                    : open.stream()
                            .filter(f -> f.getCreatedAt() != null)
                            .mapToLong(f -> java.time.Duration.between(f.getCreatedAt(), LocalDateTime.now()).toHours())
                            .average().orElse(0));
            vo.setFeedbackResolved7d(feedbackMapper.selectCount(Wrappers.<Feedback>lambdaQuery()
                    .in(Feedback::getStatus, "RESOLVED", "IGNORED")
                    .ge(Feedback::getUpdatedAt, LocalDateTime.now().minusDays(7))));
            vo.setTopErrorParams(topErrorParams());
        } catch (Exception e) {
            log.warn("质量指标聚合失败（监控页降级为空）", e);
        }
        return vo;
    }

    /** 反馈量表受保留策略约束（已处理 30 天滚动清理），全量载入分组足够小。 */
    private List<FeedbackParamStat> topErrorParams() {
        Map<String, FeedbackParamStat> byParam = new LinkedHashMap<>();
        for (Feedback feedback : feedbackMapper.selectList(Wrappers.<Feedback>lambdaQuery()
                .select(Feedback::getParamKey, Feedback::getParamName))) {
            String key = feedback.getParamKey() == null ? "整机" : feedback.getParamKey();
            FeedbackParamStat stat = byParam.computeIfAbsent(key, k -> {
                FeedbackParamStat created = new FeedbackParamStat();
                created.setParamKey(k);
                return created;
            });
            stat.setCount(stat.getCount() + 1);
            if (stat.getParamName() == null && feedback.getParamName() != null) {
                stat.setParamName(feedback.getParamName());
            }
        }
        return byParam.values().stream()
                .sorted((a, b) -> Long.compare(b.getCount(), a.getCount()))
                .limit(10)
                .peek(stat -> {
                    if (stat.getParamName() == null) {
                        stat.setParamName(stat.getParamKey());
                    }
                })
                .toList();
    }
}
