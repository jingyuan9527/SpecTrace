package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/**
 * 世代回顾聚合结果：系列机型序列 + 每个所选参数的趋势。
 */
@Data
public class EvolutionVO {

    private Long seriesId;

    private String seriesName;

    private Long brandId;

    private String brandName;

    /** 按发布时间升序，未填发布日期的机型排在最后 */
    private List<EvolutionDeviceVO> devices;

    private List<EvolutionParamVO> params;
}
