package com.spectrace.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.Data;

/**
 * 保存系列订阅抓取配置（T88）。
 */
@Data
public class SubscriptionSaveRequest {

    /** 抓取周期（月） */
    @Min(1)
    @Max(24)
    private int intervalMonths = 3;

    /** 可选指定抓取模型（ai_model.id），空用默认模型 */
    private Long aiModelId;

    /** 是否启用，false 为暂停 */
    private boolean enabled = true;
}
