package com.spectrace.dto;

import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * AgentScope 抓取完成后的回调体。
 */
@Data
public class AgentCallbackRequest {

    private Long taskId;

    private String modelName;

    private String brandName;

    private String sourceUrl;

    /** SUCCESS / FAILED */
    private String status;

    private Long durationMs;

    private String error;

    /** paramKey -> value 的结构化参数 */
    private Map<String, Object> params;

    /** paramKey -> 置信度（0~1）；缺省时由整次抓取的 confidence 兜底 */
    private Map<String, Double> paramConfidence;

    /** 存储版本与官方发布价（可选，缺省时从 params.variants 读取） */
    private List<DeviceVariantDTO> variants;

    /** 抓取日志（可选） */
    private String logs;
}
