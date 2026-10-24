package com.spectrace.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 系列总览迷你趋势的单个点：x 轴按发布年份，值已换算为参数定义单位下的数值。
 */
@Data
public class SeriesTrendPointVO {

    /** 发布年份，无日期的机型不进趋势 */
    private Integer year;

    private BigDecimal value;

    private String deviceName;
}
