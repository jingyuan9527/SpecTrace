package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/**
 * 访客流量监控（T103）：日 PV 趋势、周环比与对比功能使用量。
 */
@Data
public class TrafficMetricsVO {

    private int windowDays;

    private List<DailyPv> daily;

    /** 本周 PV 合计 */
    private long weekPv;

    /** 上周 PV 合计 */
    private long lastWeekPv;

    /** 周环比 (+x% / -x%)，上周为 0 时为 null */
    private Double weekOverWeek;

    /** 窗口期内浏览量 TOP 机型 */
    private List<TopDevicePv> topDevices;

    /** 窗口期内对比发起次数 */
    private long compareInitiated;
}
