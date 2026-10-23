package com.spectrace.controller;

import com.spectrace.common.R;
import com.spectrace.dto.LoginRequest;
import com.spectrace.dto.LoginResponse;
import com.spectrace.entity.AdminUser;
import com.spectrace.service.AuthService;
import com.spectrace.service.OperationLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final OperationLogService logService;

    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return R.ok(authService.login(req));
    }

    @GetMapping("/me")
    public R<Map<String, Object>> me(@RequestAttribute(value = "adminId", required = false) Long adminId,
                                     @RequestAttribute(value = "adminName", required = false) String adminName) {
        AdminUser user = authService.find(adminId);
        Map<String, Object> info = new HashMap<>();
        info.put("adminId", adminId);
        info.put("username", adminName);
        info.put("role", user == null ? null : user.getRole());
        // 强制改密标记随会话校验一起返回，否则刷新页面后前端就不知道还要不要拦
        info.put("mustChangePassword", authService.mustChangePassword(user));
        info.put("authenticated", SecurityContextHolder.getContext().getAuthentication() != null);
        return R.ok(info);
    }

    /**
     * 自助改密，只允许改自己的密码：账号一律取自 token，不接收前端传入的 id。
     */
    @PutMapping("/password")
    public R<Void> changePassword(@RequestBody Map<String, String> body,
                                  @RequestAttribute("adminId") Long adminId,
                                  @RequestAttribute("adminName") String adminName) {
        authService.changePassword(adminId, body.get("oldPassword"), body.get("newPassword"));
        logService.record(adminId, adminName, "CHANGE_PASSWORD", "admin_user", adminId, null);
        return R.ok();
    }
}
