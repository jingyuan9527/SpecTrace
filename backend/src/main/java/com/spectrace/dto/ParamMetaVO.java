package com.spectrace.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 单个参数值的溯源信息，与参数值本身分开返回，保证旧前端仍可直接消费 params。
 */
@Data
public class ParamMetaVO {

    private String sourceUrl;

    private BigDecimal confidence;

    private LocalDateTime updatedAt;
}
