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

    /** 所有取值是否都是纯数字；false 时前端按文本展示并提示 */
    private boolean numeric;

    private List<HistorySeriesItemVO> series;
}
