package com.spectrace.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 缺参机型排行项：浏览量越高、缺参越多的机型越值得优先补录。
 */
@Data
public class DataHealthMissingDeviceVO {

    private Long id;

    private String name;

    private Long brandId;

    private String brandName;

    private Long seriesId;

    private String seriesName;

    /** 1 上架 0 草稿/下架 */
    private Integer status;

    private LocalDate releaseDate;

    /** 近 30 天浏览量，统计不可用为 null */
    private Long pv30d;

    /** 已有参数个数 */
    private long haveParams;

    /** 启用参数总数（分母） */
    private long totalParams;
}
