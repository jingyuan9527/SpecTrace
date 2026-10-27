package com.spectrace.controller;

import com.spectrace.common.R;
import com.spectrace.dto.AssignSeriesRequest;
import com.spectrace.dto.MergeSeriesRequest;
import com.spectrace.dto.SubscriptionSaveRequest;
import com.spectrace.dto.SeriesDeviceVO;
import com.spectrace.dto.SeriesMissingDeviceVO;
import com.spectrace.dto.SeriesSuggestVO;
import com.spectrace.dto.SeriesVO;
import com.spectrace.dto.UnassignedDeviceVO;
import com.spectrace.entity.AgentSubscription;
import com.spectrace.entity.DeviceSeries;
import com.spectrace.service.AgentSubscriptionService;
import com.spectrace.service.DeviceSeriesService;
import com.spectrace.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "后台-系列管理")
@RestController
@RequestMapping("/api/admin/series")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class AdminSeriesController {

    private final DeviceSeriesService seriesService;
    private final AgentSubscriptionService subscriptionService;
    private final OperationLogService logService;

    @Operation(summary = "系列列表（含机型数与起止年份）")
    @GetMapping
    public R<List<SeriesVO>> list(@RequestParam(required = false) Long brandId) {
        return R.ok(seriesService.listOverview(brandId, null, false));
    }

    @Operation(summary = "AI 推荐品牌产品线系列划分（候选名，同名已存在会标注）")
    @GetMapping("/suggest")
    public R<List<SeriesSuggestVO>> suggest(@RequestParam Long brandId) {
        return R.ok(seriesService.suggestByAi(brandId));
    }

    @Operation(summary = "未归类机型")
    @GetMapping("/unassigned")
    public R<List<UnassignedDeviceVO>> unassigned(@RequestParam(required = false) Long brandId) {
        return R.ok(seriesService.unassigned(brandId));
    }

    @Operation(summary = "系列内机型清单（供把误分机型移到其它系列）")
    @GetMapping("/{id}/devices")
    public R<List<SeriesDeviceVO>> devices(@PathVariable Long id) {
        return R.ok(seriesService.devices(id));
    }

    @Operation(summary = "系列内缺参数的机型（补抓目标清单，含草稿）")
    @GetMapping("/{id}/devices-without-params")
    public R<List<SeriesMissingDeviceVO>> devicesWithoutParams(@PathVariable Long id) {
        return R.ok(seriesService.devicesWithoutParams(id));
    }

    @Operation(summary = "系列内参数陈旧的机型（无参数或超过 N 天未更新，供重抓全系列）")
    @GetMapping("/{id}/stale-devices")
    public R<List<SeriesMissingDeviceVO>> staleDevices(@PathVariable Long id,
                                                       @RequestParam(defaultValue = "180") int days) {
        return R.ok(seriesService.staleDevices(id, Math.max(days, 0)));
    }

    @Operation(summary = "查看系列订阅抓取配置，未订阅返回 null")
    @GetMapping("/{id}/subscription")
    public R<AgentSubscription> getSubscription(@PathVariable Long id) {
        return R.ok(subscriptionService.get(id));
    }

    @Operation(summary = "保存系列订阅抓取配置（T88，到期自动创建系列发现任务）")
    @PutMapping("/{id}/subscription")
    public R<AgentSubscription> saveSubscription(@PathVariable Long id,
                                                 @Valid @RequestBody SubscriptionSaveRequest req,
                                                 @RequestAttribute Long adminId,
                                                 @RequestAttribute String adminName) {
        AgentSubscription saved = subscriptionService.save(id, req);
        logService.record(adminId, adminName, "SAVE_SUBSCRIPTION", "agent_subscription", id,
                "周期" + req.getIntervalMonths() + "个月，" + (req.isEnabled() ? "启用" : "暂停"));
        return R.ok(saved);
    }

    @Operation(summary = "机型归类（seriesId 为空表示取消归类）")
    @PostMapping("/assign")
    public R<Integer> assign(@RequestBody AssignSeriesRequest req,
                             @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        int updated = seriesService.assign(req.getDeviceIds(), req.getSeriesId());
        DeviceSeries target = seriesService.get(req.getSeriesId());
        logService.record(adminId, adminName, "ASSIGN_SERIES", "device_series", req.getSeriesId(),
                "机型 " + req.getDeviceIds() + " 移到「" + (target == null ? "未归类" : target.getName())
                        + "」，影响 " + updated + " 台");
        return R.ok(updated);
    }

    @Operation(summary = "合并系列（机型迁到目标系列后删除源系列）")
    @PostMapping("/{id}/merge")
    public R<Integer> merge(@PathVariable Long id, @RequestBody MergeSeriesRequest req,
                            @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        int moved = seriesService.merge(id, req.getTargetId());
        logService.record(adminId, adminName, "MERGE_SERIES", "device_series", id,
                "合并到 " + req.getTargetId() + "，迁移 " + moved + " 台");
        return R.ok(moved);
    }

    @Operation(summary = "新增系列")
    @PostMapping
    public R<DeviceSeries> create(@RequestBody DeviceSeries series,
                                  @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        series.setId(null);
        DeviceSeries saved = seriesService.save(series);
        logService.record(adminId, adminName, "CREATE_SERIES", "device_series", saved.getId(), saved.getName());
        return R.ok(saved);
    }

    @Operation(summary = "编辑系列")
    @PutMapping("/{id}")
    public R<DeviceSeries> update(@PathVariable Long id, @RequestBody DeviceSeries series,
                                  @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        series.setId(id);
        DeviceSeries saved = seriesService.save(series);
        logService.record(adminId, adminName, "UPDATE_SERIES", "device_series", id, saved.getName());
        return R.ok(saved);
    }

    @Operation(summary = "删除系列（有机型在册时拒绝）")
    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id,
                          @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        seriesService.delete(id);
        logService.record(adminId, adminName, "DELETE_SERIES", "device_series", id, null);
        return R.ok();
    }
}
