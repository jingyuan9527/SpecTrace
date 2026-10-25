package com.spectrace.dto;

import lombok.Data;

/**
 * 搜索词统计行（T102）。
 */
@Data
public class SearchKeywordStat {

    private String keyword;

    private long searchCount;

    private long zeroCount;
}
