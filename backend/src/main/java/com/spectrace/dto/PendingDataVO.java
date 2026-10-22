package com.spectrace.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 待审核数据视图对象，params 为解析后的键值对。
 */
@Data
public class PendingDataVO {
    private Long id;
    private Long taskId;
    private String modelName;
    private String brandName;
    private String sourceUrl;
    private String status;
    private LocalDateTime createdAt;
    private Map<String, Object> params;
    /** 采集到的存储版本与官方发布价 */
    private List<DeviceVariantDTO> variants;
    /** 每个参数对应的中文名与分组，便于前端展示 */
    private Map<String, String> paramNames;

    /** true 表示库中尚无同品牌同名机型，审核页按「新机型」展示 */
    private Boolean newDevice;

    /** 已入库的同品牌同名机型 id，用于 diff 与覆盖更新 */
    private Long existingDeviceId;

    /** 已入库机型现有参数（paramKey -> value），供审核页标出变化项 */
    private Map<String, String> existingParams;

    /** 已入库机型现有存储版本，供审核页对比价格 */
    private List<DeviceVariantDTO> existingVariants;
}
