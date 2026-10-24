package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/**
 * 系列总览响应：系列卡片集合 + 未归类已上架机型数（品牌筛选时仅统计该品牌）。
 */
@Data
public class SeriesOverviewResult {

    private List<SeriesOverviewVO> items;

    private Long unassignedCount;
}
