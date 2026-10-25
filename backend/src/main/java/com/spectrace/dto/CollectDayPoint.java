package com.spectrace.dto;

import lombok.Data;

/**
 * 采集监控的单日数据点（只统计终态任务）。
 */
@Data
public class CollectDayPoint {

    /** yyyy-MM-dd，按任务创建日归档 */
    private String date;

    /** 当日终态任务总数（通过 + 丢弃 + 失败） */
    private long total;

    private long approved;

    private long discarded;

    private long failed;
}
