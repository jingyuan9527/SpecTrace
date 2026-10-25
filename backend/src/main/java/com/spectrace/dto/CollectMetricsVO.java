package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/**
 * 采集链路监控（T99）：近 N 天任务漏斗、成功率、耗时分布与图片下载积压。
 */
@Data
public class CollectMetricsVO {

    private int windowDays;

    private long total;

    private long approved;

    private long discarded;

    private long failed;

    /** 当前排队 / 执行中 / 待审核（存量，不受窗口限制） */
    private long queued;

    private long running;

    private long pendingReview;

    /** 成功率 = approved / (approved + failed)，无终态任务时为 null */
    private Double successRate;

    private long p50Ms;

    private long p95Ms;

    /** 耗时贴近任务超时上限的任务数（快超时说明模型或链路拖满预算） */
    private long nearTimeout;

    /** 产品图待下载数量（T90 队列积压） */
    private long imagePending;

    private List<CollectDayPoint> daily;
}
