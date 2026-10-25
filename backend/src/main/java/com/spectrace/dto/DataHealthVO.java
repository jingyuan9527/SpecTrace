package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/**
 * 数据健康度总览：全库带参率、参数覆盖排行、缺参机型排行与运营计数，
 * 让「下一步补什么」有数据依据（T85）。
 */
@Data
public class DataHealthVO {

    /** 全库机型数（后台口径，含草稿） */
    private long totalDevices;

    private long publishedDevices;

    /** 至少有一条参数值的机型数 */
    private long devicesWithParams;

    /** 全库带参率 0~1 */
    private double coverageRate;

    /** 启用中的参数定义数（缺参判定的分母） */
    private long enabledParamDefs;

    /** 近 30 天审核通过条数 */
    private long approvedLast30d;

    /** 待处理纠错反馈数 */
    private long openFeedback;

    /** 参数覆盖排行：升序，覆盖最少的参数在前 */
    private List<DataHealthParamCoverageVO> paramCoverage;

    /** 缺参机型排行：近 30 天 PV 倒序，最多 50 条 */
    private List<DataHealthMissingDeviceVO> missingDevices;
}
