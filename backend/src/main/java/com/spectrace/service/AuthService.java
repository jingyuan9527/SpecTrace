package com.spectrace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.spectrace.common.BizException;
import com.spectrace.config.JwtUtil;
import com.spectrace.dto.LoginRequest;
import com.spectrace.dto.LoginResponse;
import com.spectrace.entity.AdminUser;
import com.spectrace.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtil jwtUtil;
    private final LoginAttemptGuard loginGuard;
    private final OperationLogService logService;

    public LoginResponse login(LoginRequest req) {
        String username = req.getUsername() == null ? "" : req.getUsername().trim();
        long retryAfter = loginGuard.retryAfterSeconds(username);
        if (retryAfter > 0) {
            log.warn("登录已锁定 username={} retryAfter={}s", username, retryAfter);
            throw new BizException(429, "登录失败次数过多，请 " + retryAfter + " 秒后再试");
        }
        AdminUser user = adminUserMapper.selectOne(
                Wrappers.<AdminUser>lambdaQuery().eq(AdminUser::getUsername, username));
        if (user == null || !passwordEncoder.matches(req.getPassword(), user.getPassword())) {
            loginGuard.recordFailure(username);
            // 审计落在操作日志里，便于追溯爆破来源；对外不区分「账号不存在 / 密码错误」
            logService.record(null, username, "LOGIN_FAILED", "admin_user", user == null ? null : user.getId(),
                    user == null ? "账号不存在" : "密码错误");
            log.warn("登录失败 username={} exists={}", username, user != null);
            throw new BizException(401, "用户名或密码错误");
        }
        if (user.getStatus() == null || user.getStatus() != 1) {
            logService.record(null, username, "LOGIN_FAILED", "admin_user", user.getId(), "账号已禁用");
            throw new BizException(403, "账号已被禁用");
        }
        loginGuard.reset(username);
        LoginResponse resp = new LoginResponse();
        resp.setToken(jwtUtil.generate(user.getId(), user.getUsername(), user.getRole()));
        resp.setUserId(user.getId());
        resp.setUsername(user.getUsername());
        resp.setNickname(user.getNickname());
        resp.setRole(user.getRole());
        resp.setMustChangePassword(mustChangePassword(user));
        return resp;
    }

    public AdminUser find(Long id) {
        return id == null ? null : adminUserMapper.selectById(id);
    }

    public boolean mustChangePassword(AdminUser user) {
        return user != null && user.getMustChangePassword() != null && user.getMustChangePassword() == 1;
    }

    /**
     * 自助改密。
     *
     * <p>必须校验旧密码：JWT 只能证明「持有 token」，不能证明是本人操作，
     * 少了这一步，token 泄露就等于账号被永久接管。</p>
     */
    public void changePassword(Long adminId, String oldPassword, String newPassword) {
        String next = newPassword == null ? "" : newPassword.trim();
        if (next.length() < 6) {
            throw new BizException("新密码至少 6 位");
        }
        AdminUser user = adminUserMapper.selectById(adminId);
        if (user == null) {
            throw new BizException("账号不存在");
        }
        if (!passwordEncoder.matches(oldPassword == null ? "" : oldPassword, user.getPassword())) {
            throw new BizException("原密码不正确");
        }
        if (passwordEncoder.matches(next, user.getPassword())) {
            throw new BizException("新密码不能与原密码相同");
        }
        user.setPassword(passwordEncoder.encode(next));
        user.setMustChangePassword(0);
        adminUserMapper.updateById(user);
    }
}
