package com.spectrace.controller;

import com.spectrace.common.PageResult;
import com.spectrace.common.R;
import com.spectrace.dto.DeviceSaveRequest;
import com.spectrace.entity.Device;
import com.spectrace.service.DeviceService;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@Tag(name = "后台-机型管理")
@RestController
@RequestMapping("/api/admin/devices")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class AdminDeviceController {

    private final DeviceService deviceService;

    @GetMapping
    public R<PageResult<Device>> page(@RequestParam(defaultValue = "1") long current,
                                      @RequestParam(defaultValue = "10") long size,
                                      @RequestParam(required = false) Long brandId,
                                      @RequestParam(required = false) String keyword) {
        return R.ok(PageResult.of(deviceService.page(current, size, brandId, null, keyword)));
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

    @DeleteMapping("/{id}")
    public R<Void> delete(@PathVariable Long id,
                          @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        deviceService.delete(id, adminId, adminName);
        return R.ok();
    }
}
