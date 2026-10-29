package com.spectrace.controller;

import com.spectrace.common.R;
import com.spectrace.dto.CollectMetricsVO;
import com.spectrace.dto.ModelCallDimensionsVO;
import com.spectrace.dto.ModelCallMetricsVO;
import com.spectrace.dto.SearchMetricsVO;
import com.spectrace.dto.QualityMetricsVO;
import com.spectrace.dto.RuntimeMetricsVO;
import com.spectrace.dto.TrafficMetricsVO;
import com.spectrace.service.CollectMetricsService;
import com.spectrace.service.ModelCallMetricsService;
import com.spectrace.service.SearchLogService;
import com.spectrace.service.QualityMetricsService;
import com.spectrace.service.RuntimeMetricsService;
import com.spectrace.service.TrafficMetricsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;

/**
 * 后台运行监控（T99 起）：采集、流量、搜索、质量与模型调用的聚合读接口。
 */
@Tag(name = "后台-运行监控")
@RestController
@RequestMapping("/api/admin/metrics")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class AdminMetricsController {

    private final CollectMetricsService collectMetricsService;
    private final SearchLogService searchLogService;
    private final TrafficMetricsService trafficMetricsService;
    private final RuntimeMetricsService runtimeMetricsService;
    private final QualityMetricsService qualityMetricsService;
    private final ModelCallMetricsService modelCallMetricsService;

    @Operation(summary = "采集链路监控（漏斗 / 成功率 / 耗时 / 图片队列）")
    @GetMapping("/collect")
    public R<CollectMetricsVO> collect(@RequestParam(defaultValue = "30") int windowDays) {
        // 窗口收敛到 1~90，防止一次拉全表
        return R.ok(collectMetricsService.overview(Math.max(1, Math.min(windowDays, 90))));
    }

    @Operation(summary = "访客流量（日 PV / 周环比 / 对比使用）")
    @GetMapping("/traffic")
    public R<TrafficMetricsVO> traffic(@RequestParam(defaultValue = "30") int windowDays) {
        return R.ok(trafficMetricsService.overview(Math.max(1, Math.min(windowDays, 90))));
    }

    @Operation(summary = "技术运行时（缓存命中率 / 限流拒绝）")
    @GetMapping("/runtime")
    public R<RuntimeMetricsVO> runtime() {
        return R.ok(runtimeMetricsService.snapshot());
    }

    @Operation(summary = "数据质量（陈旧度 / 反馈时效 / 报错参数排行）")
    @GetMapping("/quality")
    public R<QualityMetricsVO> quality() {
        return R.ok(qualityMetricsService.overview());
    }

    @Operation(summary = "搜索需求信号（TOP 词 / 空结果率）")
    @GetMapping("/search")
    public R<SearchMetricsVO> search(@RequestParam(defaultValue = "30") int windowDays) {
        return R.ok(searchLogService.metrics(Math.max(1, Math.min(windowDays, 90))));
    }

    @Operation(summary = "模型调用统计（供应商 / 模型维度、趋势、延迟分位与失败分布）")
    @GetMapping("/model-calls")
    public R<ModelCallMetricsVO> modelCalls(
            @RequestParam(required = false) String providerKind,
            @RequestParam(required = false) Long providerId,
            @RequestParam(required = false) Long modelId,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime start,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime end) {
        return R.ok(modelCallMetricsService.metrics(providerKind, providerId, modelId, start, end));
    }

    @Operation(summary = "模型调用筛选项（对话连接 + 绘图供应商）")
    @GetMapping("/model-call-dimensions")
    public R<ModelCallDimensionsVO> modelCallDimensions() {
        return R.ok(modelCallMetricsService.dimensions());
    }
}
