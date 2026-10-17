package com.spectrace.dto;

import lombok.Data;

/**
 * 模型视图对象：聚合所属连接信息，供后台列表与任务模型下拉使用。
 */
@Data
public class AiModelVO {
    private Long id;
    private Long configId;
    private String configName;
    private String provider;
    private String baseUrl;
    private String model;
    private Double temperature;
    private Integer isDefault;
    private Integer enabled;
}
