package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/**
 * 同代横比中的一列：某系列在指定年份的代表机型。
 * 当年无新机时 device 为 null（仍占位，让前端呈现「该年无新机」而不是悄悄少一列）。
 */
@Data
public class GenerationSeriesVO {

    private Long seriesId;

    private String seriesName;

    private EvolutionDeviceVO device;

    private List<GenerationParamVO> params;
}
