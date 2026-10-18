package com.spectrace.agent.dto;

import lombok.Data;

import java.util.Map;

/**
 * 抓取完成后的回调体，对齐后端 {@code /api/agent/callback}。
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

    /** 抓取日志（JSON 字符串，可选） */
    private String logs;
}
