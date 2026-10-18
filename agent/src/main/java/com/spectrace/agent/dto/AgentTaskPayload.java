package com.spectrace.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 后端下发的抓取任务载荷。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class AgentTaskPayload {

    private Long taskId;
    private String modelName;
    private String brandName;

    /** 该任务指定使用的 AI 模型（ai_model.id），为空则使用默认模型 */
    private Long aiModelId;
}
