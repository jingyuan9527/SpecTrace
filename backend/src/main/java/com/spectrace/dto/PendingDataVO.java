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
}
