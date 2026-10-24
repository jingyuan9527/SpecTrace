package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/**
 * 系列总览卡片数据：收录规模沿用公开口径（只计已上架），趋势固定「屏幕尺寸 + 起售价」两条。
 */
@Data
public class SeriesOverviewVO {

    private Long id;

    private Long brandId;

    private String brandName;

    private String name;

    private Integer deviceCount;

    private Integer startYear;

    private Integer endYear;

    /** 最新一代代表机型（按发布时间），无机型时为 null */
    private EvolutionDeviceVO latest;

    /** 屏幕尺寸趋势，无数据为空列表 */
    private List<SeriesTrendPointVO> screenTrend;

    /** 起售价趋势（变体最低价），无数据为空列表 */
    private List<SeriesTrendPointVO> priceTrend;
}
