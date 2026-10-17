package com.spectrace.dto;

import lombok.Data;

import java.time.LocalDate;

@Data
public class CompareDeviceVO {
    private Long id;
    private String name;
    private Long brandId;
    private String brandName;
    private String thumbnail;
    private LocalDate releaseDate;
}
