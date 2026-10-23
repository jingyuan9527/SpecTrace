package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/**
 * 单个参数在系列各代上的取值。
 *
 * <p>{@code points} 与 {@link EvolutionVO#getDevices()} 一一对应，缺失代际为 null 值点。</p>
 *
 * <p>涨跌幅与趋势不在此计算：用户可在前端裁剪世代区间，只有前端知道当前可见范围，
 * 后端按全系列算出的值会与图表对不上。算法只保留在前端一处。</p>
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
}
