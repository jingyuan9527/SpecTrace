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

    /** 可选，任务类型（TaskType 枚举名），缺省 DEVICE_DETAIL；SERIES_DISCOVERY 时 brandName 必填 */
    private String taskType;

    /** 内部字段：订阅调度创建任务时回填来源，管理端不传 */
    private Long subscriptionId;
}
