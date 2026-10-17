package com.spectrace.dto;

import lombok.Data;

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

    /** 抓取日志（可选） */
    private String logs;
}
