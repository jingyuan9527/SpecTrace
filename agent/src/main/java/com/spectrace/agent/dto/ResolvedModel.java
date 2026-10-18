package com.spectrace.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 后端解析出的实际模型调用参数（含 API Key，仅经内部令牌接口下发）。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ResolvedModel {

    private Long modelId;
    private String provider;
    private String baseUrl;
    private String apiKey;
    private String model;
    private Double temperature;
}
