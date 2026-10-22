package com.spectrace.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

@Data
public class CompareDeviceVO {
    private Long id;
    private String name;
    private Long brandId;
    private String brandName;
    private String thumbnail;
    private LocalDate releaseDate;

    /** 存储版本与价格（按 sortOrder 升序） */
    private List<DeviceVariantDTO> variants;

    /** 全系最低发布价，便于卡片与对比表展示 */
    private BigDecimal minPrice;
}
