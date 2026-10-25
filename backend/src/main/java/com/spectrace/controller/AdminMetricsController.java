package com.spectrace.controller;

import com.spectrace.common.R;
import com.spectrace.dto.CollectMetricsVO;
import com.spectrace.service.CollectMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 后台运行监控（T99 起）：采集、流量、搜索、质量的聚合读接口。
 */
@Tag(name = "后台-运行监控")
@RestController
@RequestMapping("/api/admin/metrics")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class AdminMetricsController {

    private final CollectMetricsService collectMetricsService;

    @Operation(summary = "采集链路监控（漏斗 / 成功率 / 耗时 / 图片队列）")
    @GetMapping("/collect")
    public R<CollectMetricsVO> collect(@RequestParam(defaultValue = "30") int windowDays) {
        // 窗口收敛到 1~90，防止一次拉全表
        return R.ok(collectMetricsService.overview(Math.max(1, Math.min(windowDays, 90))));
    }
}
