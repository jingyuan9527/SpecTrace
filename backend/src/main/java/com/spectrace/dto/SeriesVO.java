package com.spectrace.dto;

import lombok.Data;

/**
 * 世代回顾筛选用的系列概览：机型数与起止年份用于前端展示「共 N 代 · 2021-2024」。
 */
@Data
public class SeriesVO {

    private Long id;

    private Long brandId;

    private String brandName;

    private String name;

    private Integer sortOrder;

    /** 1 启用 0 停用 */
    private Integer status;

    private Integer deviceCount;

    /** 系列内已有参数值的机型数（与 deviceCount 同口径、含草稿），完善度 = paramDeviceCount / deviceCount */
    private Integer paramDeviceCount;

    /** 系列内最早/最晚发布年份，无机型或日期全空时为 null */
    private Integer startYear;

    private Integer endYear;
}
