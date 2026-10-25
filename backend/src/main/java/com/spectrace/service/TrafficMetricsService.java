package com.spectrace.service;

import com.spectrace.dto.TrafficMetricsVO;
import com.spectrace.mapper.DeviceViewDailyMapper;
import com.spectrace.mapper.MetricDailyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/**
 * 访客流量监控（T103）：全部现成聚合数据的只读汇总。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TrafficMetricsService {

    static final String METRIC_COMPARE = "compare_initiated";

    private final DeviceViewDailyMapper viewMapper;
    private final MetricDailyMapper metricMapper;

    /** 对比发起计数：一次对比请求记 1（设备维度 PV 由 device_view_daily 另行承担）。 */
    public void recordCompare() {
        try {
            metricMapper.upsertIncrement(METRIC_COMPARE, LocalDate.now().toString());
        } catch (Exception e) {
            log.warn("对比计数失败（不影响对比接口）", e);
        }
    }

    public TrafficMetricsVO overview(int windowDays) {
        TrafficMetricsVO vo = new TrafficMetricsVO();
        vo.setWindowDays(windowDays);
        try {
            LocalDate today = LocalDate.now();
            vo.setDaily(viewMapper.selectDailyPv(today.minusDays(windowDays - 1L).toString()));
            // 周环比按「本周（周一起）vs 上周同期自然周」口径
            LocalDate weekStart = today.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
            vo.setWeekPv(viewMapper.sumPvBetween(weekStart.toString(), today.plusDays(1).toString()));
            vo.setLastWeekPv(viewMapper.sumPvBetween(weekStart.minusWeeks(1).toString(), weekStart.toString()));
            vo.setWeekOverWeek(vo.getLastWeekPv() == 0 ? null
                    : (double) (vo.getWeekPv() - vo.getLastWeekPv()) / vo.getLastWeekPv());
            vo.setTopDevices(viewMapper.selectTopDevices(today.minusDays(windowDays - 1L).toString(), 10));
            vo.setCompareInitiated(metricMapper.sumSince(METRIC_COMPARE, today.minusDays(windowDays - 1L).toString()));
        } catch (Exception e) {
            log.warn("流量指标聚合失败（监控页降级为空）", e);
        }
        return vo;
    }
}
