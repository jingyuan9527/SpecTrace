package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/**
 * 数据质量观测（T106）：陈旧度分布、反馈处理时效与被报错参数排行。
 */
@Data
public class QualityMetricsVO {

    /** 参数最近更新超过阈值（默认 180 天）的机型数——重抓全系列的存量依据 */
    private long staleDevices;

    private int staleThresholdDays;

    /** 当前待处理反馈数 */
    private long feedbackOpen;

    /** 待处理反馈的平均滞留小时数，无待处理为 null */
    private Double feedbackAvgAgeHours;

    /** 近 7 天处理（RESOLVED + IGNORED）条数 */
    private long feedbackResolved7d;

    /** 被报错最多的参数 TOP10 */
    private List<FeedbackParamStat> topErrorParams;
}
