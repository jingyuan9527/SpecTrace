package com.spectrace.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 同代横比中某个参数在某台代表机型上的单点取值。
 *
 * <p>字段口径与 {@link EvolutionPointVO} 一致：raw 为库中原始文本，value 为按单位换算后的数值，
 * conflict 表示原始单位与参数定义量纲不一致。</p>
 */
@Data
public class GenerationParamVO {

    private Long paramDefId;

    private String paramName;

    private String unit;

    private boolean numeric;

    private BigDecimal value;

    private String raw;

    private boolean conflict;
}
