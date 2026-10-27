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

    /**
     * 被报错最多的参数 TOP10：分组下推给 SQL。
     *
     * <p>反馈表虽受保留策略约束，但监控页每分钟可能被刷新，全表载入的代价随站点增长只会更差。</p>
     */
    private List<FeedbackParamStat> topErrorParams() {
        List<Map<String, Object>> rows = feedbackMapper.selectMaps(
                new com.baomidou.mybatisplus.core.conditions.query.QueryWrapper<Feedback>()
                        .select("COALESCE(param_key, '整机') AS param_key",
                                "MAX(param_name) AS param_name",
                                "COUNT(*) AS cnt")
                        .groupBy("COALESCE(param_key, '整机')")
                        .orderByDesc("cnt")
                        .last("LIMIT 10"));
        return rows.stream().map(row -> {
            String key = row.get("param_key") == null ? "整机" : String.valueOf(row.get("param_key"));
            FeedbackParamStat stat = new FeedbackParamStat();
            stat.setParamKey(key);
            Object name = row.get("param_name");
            stat.setParamName(name == null ? key : String.valueOf(name));
            Object count = row.get("cnt");
            stat.setCount(count instanceof Number number ? number.longValue() : 0L);
            return stat;
        }).toList();
    }
}
