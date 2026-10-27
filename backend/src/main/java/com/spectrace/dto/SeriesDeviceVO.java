package com.spectrace.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 系列内机型清单：后台在系列管理里查看该系列现有归属，并把误分机型移到其它系列。
 */
@Data
public class SeriesDeviceVO {

    private Long id;

    private String name;

    private Long brandId;

    /** 当前所属系列，移动后由系列清单接口重新返回 */
    private Long seriesId;

    /** 0 草稿 1 上架 */
    private Integer status;

    private LocalDate releaseDate;

    private String thumbnail;
}
