package com.spectrace.controller;

import com.spectrace.common.PageResult;
import com.spectrace.common.R;
import com.spectrace.entity.AdminUser;
import com.spectrace.service.AdminUserService;
import com.spectrace.service.OperationLogService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@Tag(name = "后台-账号权限管理")
@RestController
@RequestMapping("/api/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserService adminUserService;
    private final OperationLogService logService;

    @GetMapping
    @PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
    public R<PageResult<AdminUser>> page(@RequestParam(defaultValue = "1") long current,
                                         @RequestParam(defaultValue = "10") long size) {
        return R.ok(PageResult.of(adminUserService.page(current, size)));
    }

    @PostMapping
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public R<AdminUser> create(@RequestBody Map<String, String> body,
                               @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        AdminUser user = adminUserService.create(body);
        logService.record(adminId, adminName, "CREATE_USER", "admin_user", user.getId(), user.getUsername());
        return R.ok(user);
    }

    @PutMapping("/{id}/role")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public R<Void> updateRole(@PathVariable Long id, @RequestParam String role,
                              @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        adminUserService.updateRole(id, role);
        logService.record(adminId, adminName, "UPDATE_USER_ROLE", "admin_user", id, role);
        return R.ok();
    }

    @PutMapping("/{id}/status")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public R<Void> toggleStatus(@PathVariable Long id,
                                @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        adminUserService.toggleStatus(id);
        logService.record(adminId, adminName, "TOGGLE_USER_STATUS", "admin_user", id, null);
        return R.ok();
    }

    @PutMapping("/{id}/password")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public R<Void> resetPassword(@PathVariable Long id, @RequestParam String password,
                                 @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        adminUserService.resetPassword(id, password);
        logService.record(adminId, adminName, "RESET_USER_PASSWORD", "admin_user", id, null);
        return R.ok();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public R<Void> delete(@PathVariable Long id,
                          @RequestAttribute Long adminId, @RequestAttribute String adminName) {
        adminUserService.delete(id);
        logService.record(adminId, adminName, "DELETE_USER", "admin_user", id, null);
        return R.ok();
    }
}
