package com.spectrace.controller;

import com.spectrace.common.R;
import com.spectrace.dto.AssignSeriesRequest;
import com.spectrace.dto.MergeSeriesRequest;
import com.spectrace.dto.SeriesSuggestVO;
import com.spectrace.dto.SeriesVO;
import com.spectrace.dto.UnassignedDeviceVO;
import com.spectrace.entity.DeviceSeries;
import com.spectrace.service.DeviceSeriesService;
import com.spectrace.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
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
    private final OperationLogService logService;

    @Operation(summary = "系列列表（含机型数与起止年份）")
    @GetMapping
    public R<List<SeriesVO>> list(@RequestParam(required = false) Long brandId) {
        return R.ok(seriesService.listOverview(brandId, null));
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

    @Operation(summary = "机型归类（seriesId 为空表示取消归类）")
    @PostMapping("/assign")
    public R<Integer> assign(@RequestBody AssignSeriesRequest req,
                             @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        int updated = seriesService.assign(req.getDeviceIds(), req.getSeriesId());
        logService.record(adminId, adminName, "ASSIGN_SERIES", "device_series", req.getSeriesId(),
                "机型 " + req.getDeviceIds() + " 归类，影响 " + updated + " 台");
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
