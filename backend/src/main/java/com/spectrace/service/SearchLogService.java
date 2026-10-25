package com.spectrace.service;

import com.spectrace.dto.SearchKeywordStat;
import com.spectrace.dto.SearchMetricsVO;
import com.spectrace.mapper.SearchLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

/**
 * 搜索词记录（T102）：写入尽力而为——失败只记日志绝不阻塞联想接口；
 * 读取失败返回空指标（监控页照常渲染其它卡片）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchLogService {

    private static final int KEYWORD_MAX = 64;

    private final SearchLogMapper searchLogMapper;

    public void record(String keyword, int hitCount) {
        if (keyword == null || keyword.isBlank()) {
            return;
        }
        String trimmed = keyword.trim();
        if (trimmed.length() > KEYWORD_MAX) {
            trimmed = trimmed.substring(0, KEYWORD_MAX);
        }
        try {
            searchLogMapper.upsertIncrement(trimmed, LocalDate.now().toString(), hitCount == 0 ? 1 : 0);
        } catch (Exception e) {
            log.warn("搜索词记录失败（不影响联想接口）: keyword={}", trimmed, e);
        }
    }

    public SearchMetricsVO metrics(int windowDays) {
        SearchMetricsVO vo = new SearchMetricsVO();
        vo.setWindowDays(windowDays);
        try {
            String since = LocalDate.now().minusDays(windowDays).toString();
            SearchKeywordStat totals = searchLogMapper.totals(since);
            vo.setTotalSearches(totals == null ? 0 : totals.getSearchCount());
            vo.setZeroSearches(totals == null ? 0 : totals.getZeroCount());
            vo.setZeroRate(vo.getTotalSearches() == 0 ? null
                    : (double) vo.getZeroSearches() / vo.getTotalSearches());
            vo.setTopKeywords(searchLogMapper.topKeywords(since, 10));
            vo.setZeroTop(searchLogMapper.zeroTop(since, 10));
        } catch (Exception e) {
            log.warn("搜索指标聚合失败（监控页降级为空）", e);
        }
        return vo;
    }
}
