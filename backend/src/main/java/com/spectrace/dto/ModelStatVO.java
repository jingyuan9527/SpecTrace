package com.spectrace.dto;

import lombok.Data;

/**
 * 单个 AI 模型的采集表现统计（T101）：哪个模型又慢又错一目了然，是「重试换模型」的决策依据。
 */
@Data
public class ModelStatVO {

    private Long aiModelId;

    /** 供应商连接名 / 模型名，任一缺失时降级显示另一段 */
    private String label;

    private long total;

    private long approved;

    private long failed;

    /** approved / (approved + failed)，无终态为 null */
    private Double successRate;

    private long avgMs;

    private long p95Ms;

    private long nearTimeout;
}
