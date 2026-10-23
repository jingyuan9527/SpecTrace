package com.spectrace.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 机型新增/编辑请求。params 以 paramKey -> value 传入，value 统一为字符串。
 */
@Data
public class DeviceSaveRequest {

    /** 为空表示新增，非空表示编辑 */
    private Long id;

    private Long brandId;

    /** 所属系列；为空表示未归类 */
    private Long seriesId;

    @NotBlank
    private String name;

    private LocalDate releaseDate;

    private String thumbnail;

    private String description;

    private Integer status;

    private Map<String, String> params;

    /** paramKey -> 置信度（0~1）；审核通过时沿用采集结果，人工保存为 null 表示人工确认 */
    private Map<String, Double> paramConfidence;

    /** 存储版本与价格；为 null 时不改动既有版本，传空列表则清空 */
    private List<DeviceVariantDTO> variants;
}
