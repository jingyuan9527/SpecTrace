package com.spectrace.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 单个参数值的溯源信息，与参数值本身分开返回，保证旧前端仍可直接消费 params。
 */
@Data
public class ParamMetaVO {

    /** 参数定义 ID，详情页参数行据此拼接「参数历史」链接 */
    private Long paramDefId;

    private String sourceUrl;

    private BigDecimal confidence;

    private LocalDateTime updatedAt;
}
