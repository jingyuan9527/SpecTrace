package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/**
 * 多品牌参数历史：同一参数在多个品牌上的时间序列。
 */
@Data
public class HistorySeriesVO {

    private Long paramDefId;

    private String paramName;

    private String unit;

    /** 所有取值是否能解析为数值；false 时前端按文本展示并提示 */
    private boolean numeric;

    /** 与参数定义单位不一致或无法换算的取值，例如「iPhone 16：6.1寸」 */
    private List<String> unitConflicts;

    private List<HistorySeriesItemVO> series;
}
