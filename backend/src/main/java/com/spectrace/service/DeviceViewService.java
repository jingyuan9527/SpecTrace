package com.spectrace.service;

import com.spectrace.dto.DevicePv;
import com.spectrace.mapper.DeviceViewDailyMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 访客行为统计：写入必须「尽力而为」——统计失败只记日志，绝不拖垮公开接口主流程；
 * 读取失败由调用方回落到旧口径（发布时间倒序），保证首页永远有内容。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DeviceViewService {

    /** 热门口径窗口：近 30 天浏览量 */
    public static final int HOT_WINDOW_DAYS = 30;

    private final DeviceViewDailyMapper viewMapper;
    private final DeviceViewAggregator aggregator;

    /**
     * 记录浏览：只写内存缓冲，落库由 {@link DeviceViewAggregator} 定时批量完成。
     * 统计失败不影响公开接口——这也是把它从请求线程摘出去的另一半原因。
     */
    public void recordViews(String clientIp, Collection<Long> deviceIds) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return;
        }
        aggregator.record(clientIp, deviceIds);
    }

    /** 热门机型 id 列表（PV 倒序）；窗口内无数据返回空，调用方回落。 */
    public List<Long> hotDeviceIds(int limit) {
        try {
            return viewMapper.selectHotDeviceIds(sinceDate(HOT_WINDOW_DAYS), limit);
        } catch (Exception e) {
            log.warn("热门机型统计失败（回落发布时间口径）: windowDays={}", HOT_WINDOW_DAYS, e);
            return List.of();
        }
    }

    /** 批量查询机型近 N 天 PV，缺数据的机型记 0；统计失败返回空 Map（列表照常展示）。 */
    public Map<Long, Long> pvOf(Collection<Long> deviceIds, int days) {
        if (deviceIds == null || deviceIds.isEmpty()) {
            return Map.of();
        }
        try {
            return viewMapper.selectPvSince(sinceDate(days), deviceIds).stream()
                    .collect(Collectors.toMap(DevicePv::getDeviceId, DevicePv::getPv, (a, b) -> a));
        } catch (Exception e) {
            log.warn("浏览量批量统计失败: size={} days={}", deviceIds.size(), days, e);
            return Map.of();
        }
    }

    public int cleanupBefore(int days) {
        return viewMapper.deleteBefore(LocalDate.now().minusDays(days).toString());
    }

    public long count() {
        return viewMapper.selectCount(null);
    }

    private String sinceDate(int days) {
        return LocalDate.now().minusDays(days).toString();
    }
}
