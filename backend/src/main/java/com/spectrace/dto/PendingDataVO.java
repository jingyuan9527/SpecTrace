package com.spectrace.dto;

import lombok.Data;

import java.time.LocalDateTime;
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
    /** 每个参数对应的中文名与分组，便于前端展示 */
    private Map<String, String> paramNames;
}
