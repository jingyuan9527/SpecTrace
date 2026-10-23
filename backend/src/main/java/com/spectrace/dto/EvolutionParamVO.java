package com.spectrace.dto;

import lombok.Data;

import java.math.BigDecimal;
import java.util.List;

/**
 * 单个参数在系列各代上的取值、涨跌幅与趋势。
 *
 * <p>{@code points} 与 {@link EvolutionVO#getDevices()} 一一对应，缺失代际为 null 值点。</p>
 */
@Data
public class EvolutionParamVO {

    private Long paramDefId;

    private String paramKey;

    private String paramName;

    private String unit;

    /** 全部非缺失取值都能解析为数值时为 true，否则前端只能按文本展示 */
    private boolean numeric;

    private List<EvolutionPointVO> points;

    private Integer missingCount;

    /** 首个与末个非缺失数值，无法计算时为 null */
    private BigDecimal firstValue;

    private BigDecimal lastValue;

    /** 末值相对首值的涨跌幅（%），首值为 0 或非数值时为 null */
    private BigDecimal changeRate;

    /** UP / DOWN / FLAT / UNKNOWN */
    private String trend;
}
