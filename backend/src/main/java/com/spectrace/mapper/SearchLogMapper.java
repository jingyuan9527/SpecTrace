package com.spectrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spectrace.dto.SearchKeywordStat;
import com.spectrace.entity.SearchLog;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

public interface SearchLogMapper extends BaseMapper<SearchLog> {

    /** SQLite 原子 upsert：一次语句同时累加搜索次数与空结果次数。 */
    @Insert("""
            INSERT INTO search_log (keyword, search_date, search_count, zero_count)
            VALUES (#{keyword}, #{date}, 1, #{zero})
            ON CONFLICT(keyword, search_date) DO UPDATE SET
                search_count = search_count + 1,
                zero_count = zero_count + #{zero}
            """)
    int upsertIncrement(@Param("keyword") String keyword, @Param("date") String date, @Param("zero") int zero);

    @Select("""
            SELECT keyword, SUM(search_count) AS searchCount, SUM(zero_count) AS zeroCount
            FROM search_log
            WHERE search_date >= #{sinceDate}
            GROUP BY keyword
            ORDER BY searchCount DESC, keyword ASC
            LIMIT #{limit}
            """)
    List<SearchKeywordStat> topKeywords(@Param("sinceDate") String sinceDate, @Param("limit") int limit);

    @Select("""
            SELECT keyword, SUM(search_count) AS searchCount, SUM(zero_count) AS zeroCount
            FROM search_log
            WHERE search_date >= #{sinceDate} AND zero_count > 0
            GROUP BY keyword
            ORDER BY zeroCount DESC, keyword ASC
            LIMIT #{limit}
            """)
    List<SearchKeywordStat> zeroTop(@Param("sinceDate") String sinceDate, @Param("limit") int limit);

    /** 窗口内总量（keyword 为空），无数据时行仍返回（值为 0） */
    @Select("""
            SELECT NULL AS keyword, COALESCE(SUM(search_count), 0) AS searchCount, COALESCE(SUM(zero_count), 0) AS zeroCount
            FROM search_log
            WHERE search_date >= #{sinceDate}
            """)
    SearchKeywordStat totals(@Param("sinceDate") String sinceDate);

    /** 保留策略：按天聚合只留最近 N 天。 */
    @Delete("DELETE FROM search_log WHERE search_date < #{beforeDate}")
    int deleteBefore(@Param("beforeDate") String beforeDate);
}
