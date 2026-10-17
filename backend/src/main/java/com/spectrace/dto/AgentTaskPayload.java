package com.spectrace.dto;

import lombok.Data;

/**
 * 回传给 AgentScope 的任务载荷。
 */
@Data
public class AgentTaskPayload {
    private Long taskId;
    private String modelName;
    private String brandName;
    /** 该任务指定使用的 AI 模型，为空则由 Agent 用默认模型 */
    private Long aiModelId;
}
