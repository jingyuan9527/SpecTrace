package com.spectrace.dto;

import lombok.Data;

/**
 * 失败原因分布的一行。
 */
@Data
public class ModelCallFailureVO {

    private String reason;

    private String label;

    private long count;

    /** count / failed，无失败为 null */
    private Double share;
}
