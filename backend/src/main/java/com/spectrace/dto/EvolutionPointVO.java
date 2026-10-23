package com.spectrace.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 单个参数在某一代机型上的取值。
 *
 * <p>{@code raw} 为库中原始文本，{@code value} 为按单位换算后的数值；
 * {@code conflict} 表示原始单位与参数定义量纲不一致，前端应提示按原值展示。</p>
 */
@Data
public class EvolutionPointVO {

    private Long deviceId;

    private String raw;

    private BigDecimal value;

    private boolean conflict;
}
