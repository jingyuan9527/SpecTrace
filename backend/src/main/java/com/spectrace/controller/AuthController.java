package com.spectrace.controller;

import com.spectrace.common.R;
import com.spectrace.dto.LoginRequest;
import com.spectrace.dto.LoginResponse;
import com.spectrace.service.AuthService;
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

    @PostMapping("/login")
    public R<LoginResponse> login(@Valid @RequestBody LoginRequest req) {
        return R.ok(authService.login(req));
    }

    @GetMapping("/me")
    public R<Map<String, Object>> me(@RequestAttribute(value = "adminId", required = false) Long adminId,
                                     @RequestAttribute(value = "adminName", required = false) String adminName) {
        Map<String, Object> info = new HashMap<>();
        info.put("adminId", adminId);
        info.put("username", adminName);
        info.put("authenticated", SecurityContextHolder.getContext().getAuthentication() != null);
        return R.ok(info);
    }
}
