package com.spectrace.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.spectrace.entity.MetricDaily;
import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

public interface MetricDailyMapper extends BaseMapper<MetricDaily> {

    @Insert("""
            INSERT INTO metric_daily (metric_key, metric_date, value) VALUES (#{key}, #{date}, 1)
            ON CONFLICT(metric_key, metric_date) DO UPDATE SET value = value + 1
            """)
    int upsertIncrement(@Param("key") String key, @Param("date") String date);

    @Select("SELECT COALESCE(SUM(value), 0) FROM metric_daily WHERE metric_key = #{key} AND metric_date >= #{sinceDate}")
    long sumSince(@Param("key") String key, @Param("sinceDate") String sinceDate);

    /** 保留策略：与浏览量同窗口滚动清理。 */
    @Delete("DELETE FROM metric_daily WHERE metric_date < #{beforeDate}")
    int deleteBefore(@Param("beforeDate") String beforeDate);
}
