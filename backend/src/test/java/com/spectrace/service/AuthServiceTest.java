package com.spectrace.service;

import com.spectrace.common.BizException;
import com.spectrace.config.JwtUtil;
import com.spectrace.dto.LoginRequest;
import com.spectrace.entity.AdminUser;
import com.spectrace.mapper.AdminUserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** 改密是账号被接管时的最后一道防线，旧密码校验与强制改密标记必须锁死。 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    @Mock
    private AdminUserMapper adminUserMapper;
    @Mock
    private JwtUtil jwtUtil;
    @Mock
    private OperationLogService operationLogService;

    /** 用真实编码器，避免 mock 掉 matches 后把校验逻辑也一起跳过 */
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    /** 用真实锁定器，保证「连续失败 → 锁定」的计数语义被原样覆盖 */
    private final LoginAttemptGuard loginGuard = new LoginAttemptGuard();

    private AuthService service;
    private AdminUser user;

    @BeforeEach
    void setUp() {
        service = new AuthService(adminUserMapper, passwordEncoder, jwtUtil, loginGuard, operationLogService);

        user = new AdminUser();
        user.setId(1L);
        user.setUsername("admin");
        user.setPassword(passwordEncoder.encode("admin123"));
        user.setRole("SUPER_ADMIN");
        user.setStatus(1);
        user.setMustChangePassword(1);
        when(adminUserMapper.selectById(1L)).thenReturn(user);
    }

    @Test
    @DisplayName("旧密码错误时拒绝，且不改动账号")
    void rejectsWrongOldPassword() {
        assertThatThrownBy(() -> service.changePassword(1L, "wrong-pass", "newpass123"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("原密码不正确");
        verify(adminUserMapper, never()).updateById(any(AdminUser.class));
    }

    @Test
    @DisplayName("新密码过短或与原密码相同都拒绝")
    void validatesNewPassword() {
        assertThatThrownBy(() -> service.changePassword(1L, "admin123", "123"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("至少 6 位");

        assertThatThrownBy(() -> service.changePassword(1L, "admin123", "admin123"))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("不能与原密码相同");
        verify(adminUserMapper, never()).updateById(any(AdminUser.class));
    }

    @Test
    @DisplayName("改密成功后清除强制改密标记")
    void clearsMustChangeFlag() {
        service.changePassword(1L, "admin123", "newpass123");

        verify(adminUserMapper).updateById(any(AdminUser.class));
        assertThat(user.getMustChangePassword()).isZero();
        assertThat(passwordEncoder.matches("newpass123", user.getPassword())).isTrue();
    }

    @Test
    @DisplayName("仍在使用初始密码的账号登录时返回强制改密标记")
    void loginFlagsDefaultPassword() {
        when(adminUserMapper.selectOne(any())).thenReturn(user);
        when(jwtUtil.generate(any(), any(), any())).thenReturn("token");

        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("admin123");

        assertThat(service.login(req).getMustChangePassword()).isTrue();

        user.setMustChangePassword(0);
        assertThat(service.login(req).getMustChangePassword()).isFalse();
    }

    @Test
    @DisplayName("连续失败达到阈值后登录被锁定，锁定期内正确密码也拒绝")
    void locksAfterRepeatedFailures() {
        when(adminUserMapper.selectOne(any())).thenReturn(null);
        LoginRequest req = new LoginRequest();
        req.setUsername("admin");
        req.setPassword("bad-pass");

        for (int i = 0; i < 5; i++) {
            assertThatThrownBy(() -> service.login(req))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining("用户名或密码错误");
        }
        assertThatThrownBy(() -> service.login(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("失败次数过多");

        // 锁定期内即使密码正确也拒绝，防止绕过
        when(adminUserMapper.selectOne(any())).thenReturn(user);
        req.setPassword("admin123");
        assertThatThrownBy(() -> service.login(req))
                .isInstanceOf(BizException.class)
                .hasMessageContaining("失败次数过多");
    }
}
