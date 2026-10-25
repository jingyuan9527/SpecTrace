package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/**
 * 搜索需求信号（T102）：总搜索量、空结果率与两个排行。
 */
@Data
public class SearchMetricsVO {

    private int windowDays;

    private long totalSearches;

    private long zeroSearches;

    /** 空结果率 0~1，无搜索为 null */
    private Double zeroRate;

    /** 搜索次数 TOP（含空结果占比列） */
    private List<SearchKeywordStat> topKeywords;

    /** 空结果次数 TOP：库里有缺口的直接线索 */
    private List<SearchKeywordStat> zeroTop;
}
