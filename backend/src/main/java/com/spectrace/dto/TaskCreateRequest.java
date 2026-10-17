package com.spectrace.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TaskCreateRequest {
    @NotBlank
    private String modelName;
    /** 可选，减少 Agent 搜索歧义 */
    private String brandName;
    /** 可选，指定抓取使用的 AI 模型（ai_model.id），为空用默认模型 */
    private Long aiModelId;
}
