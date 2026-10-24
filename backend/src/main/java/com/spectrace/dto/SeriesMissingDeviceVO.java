package com.spectrace.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 系列内缺参数的机型（含草稿）：「补抓缺参机型」的目标清单。
 */
@Data
public class SeriesMissingDeviceVO {

    private Long id;

    private String name;

    /** 0 草稿 1 上架 */
    private Integer status;

    private LocalDate releaseDate;

    private String thumbnail;
}
