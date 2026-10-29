package com.spectrace.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 模型调用统计读数：时间序列、延迟分位、成功率和失败原因分布。
 *
 * <p>延迟口径包含失败调用——超时调用的耗时本身就是最有价值的信号之一。</p>
 */
@Data
public class ModelCallMetricsVO {

    private LocalDateTime start;

    private LocalDateTime end;

    /** 趋势分桶粒度：MINUTE / HOUR / DAY */
    private String bucketUnit;

    private long total;

    private long success;

    private long failed;

    /** success / total，无调用为 null */
    private Double successRate;

    private long avgMs;

    private long p50Ms;

    private long p95Ms;

    private long p99Ms;

    private long maxMs;

    private List<ModelCallPointVO> series;

    private List<ModelCallFailureVO> failures;
}
