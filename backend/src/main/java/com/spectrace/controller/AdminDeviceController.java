package com.spectrace.controller;

import com.spectrace.common.PageResult;
import com.spectrace.common.R;
import com.spectrace.dto.DeviceBatchStatusRequest;
import com.spectrace.dto.DeviceImportResult;
import com.spectrace.dto.DeviceSaveRequest;
import com.spectrace.entity.Device;
import com.spectrace.service.AgentTaskService;
import com.spectrace.service.DeviceImportService;
import com.spectrace.service.DeviceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@Tag(name = "后台-机型管理")
@RestController
@RequestMapping("/api/admin/devices")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class AdminDeviceController {

    private final DeviceService deviceService;
    private final DeviceImportService deviceImportService;
    private final AgentTaskService agentTaskService;

    @Operation(summary = "下载机型批量导入 CSV 模板")
    @GetMapping("/import/template")
    public ResponseEntity<byte[]> importTemplate() {
        byte[] body = deviceImportService.template();
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"device-import-template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
                .body(body);
    }

    @Operation(summary = "机型批量导入（dryRun=true 仅预览校验）")
    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public R<DeviceImportResult> importDevices(@RequestParam("file") MultipartFile file,
                                               @RequestParam(defaultValue = "true") boolean dryRun,
                                               @RequestAttribute Long adminId,
                                               @RequestAttribute String adminName) {
        return R.ok(deviceImportService.importCsv(file, dryRun, adminId, adminName));
    }

    @GetMapping
    public R<PageResult<Device>> page(@RequestParam(defaultValue = "1") long current,
                                      @RequestParam(defaultValue = "10") long size,
                                      @RequestParam(required = false) Long brandId,
                                      @RequestParam(required = false) Long seriesId,
                                      @RequestParam(required = false) String keyword) {
        var page = deviceService.page(current, size, brandId, seriesId, null, keyword);
        // 列表徽标：标注同品牌同名机型有未处理的抓取结果，管理员不用两边来回对
        Map<Long, Integer> pendingCounts = agentTaskService.pendingCountByDevice(page.getRecords());
        page.getRecords().forEach(d -> d.setPendingReviewCount(pendingCounts.getOrDefault(d.getId(), 0)));
        return R.ok(PageResult.of(page));
    }

    @GetMapping("/{id}")
    public R<java.util.Map<String, Object>> detail(@PathVariable Long id) {
        return R.ok(deviceService.detail(id));
    }

    @PostMapping
    public R<Device> create(@Valid @RequestBody DeviceSaveRequest req,
                            @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        req.setId(null);
        return R.ok(deviceService.save(req, adminId, adminName));
    }

    @PutMapping("/{id}")
    public R<Device> update(@PathVariable Long id, @Valid @RequestBody DeviceSaveRequest req,
                            @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        req.setId(id);
        return R.ok(deviceService.save(req, adminId, adminName));
    }

    @Operation(summary = "批量上架/下架（status：1 上架、0 下架）")
    @PutMapping("/batch-status")
    public R<Integer> batchStatus(@Valid @RequestBody DeviceBatchStatusRequest req,
                                  @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        return R.ok(deviceService.batchUpdateStatus(req.getIds(), req.getStatus(), adminId, adminName));
    }

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id,
                          @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        deviceService.delete(id, adminId, adminName);
        return R.ok();
    }
}
