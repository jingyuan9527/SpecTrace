package com.spectrace.dto;

import lombok.Data;

/**
 * 模型调用趋势的单个时间桶。
 */
@Data
public class ModelCallPointVO {

    /** 按 bucketUnit 格式化的桶起点，如 2026-10-29 10:00 */
    private String bucket;

    private long total;

    private long success;

    private long failed;

    private long avgMs;

    private long p95Ms;

    private long maxMs;
}
