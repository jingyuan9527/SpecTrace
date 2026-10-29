package com.spectrace.dto;

import lombok.Data;

/**
 * AgentScope 解析后的实际模型调用参数（含 API Key，仅经内部令牌接口下发）。
 */
@Data
public class ResolvedModel {
    private Long configId;
    private String configName;
    private Long modelId;
    private String provider;
    private String baseUrl;
    private String apiKey;
    private String model;
    private Double temperature;
}
