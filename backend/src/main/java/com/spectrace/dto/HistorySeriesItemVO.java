package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/**
 * 单个品牌在某参数上的历史数据点。
 */
@Data
public class HistorySeriesItemVO {

    private Long brandId;

    private String brandName;

    private List<HistoryPoint> points;
}
