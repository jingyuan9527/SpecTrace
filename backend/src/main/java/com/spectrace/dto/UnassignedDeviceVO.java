package com.spectrace.dto;

import lombok.Data;

import java.time.LocalDate;

/**
 * 未归类机型：后台系列管理页可直接为其选择系列即时归类。
 */
@Data
public class UnassignedDeviceVO {

    private Long id;

    private String name;

    private Long brandId;

    private String brandName;

    private LocalDate releaseDate;

    private String thumbnail;
}
