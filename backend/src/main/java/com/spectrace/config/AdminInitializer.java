package com.spectrace.config;

import com.spectrace.entity.AdminUser;
import com.spectrace.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * 首次启动时创建超级管理员账号，密码使用 BCrypt 加密后写入。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminInitializer implements ApplicationRunner {

    /** 初始口令：仅在 admin_user 表为空时使用，登录后强制修改 */
    public static final String DEFAULT_PASSWORD = "admin123";

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        Long count = adminUserMapper.selectCount(null);
        if (count != null && count > 0) {
            return;
        }
        AdminUser admin = new AdminUser();
        admin.setUsername("admin");
        admin.setPassword(passwordEncoder.encode(DEFAULT_PASSWORD));
        admin.setNickname("超级管理员");
        admin.setRole("SUPER_ADMIN");
        admin.setStatus(1);
        admin.setMustChangePassword(1);
        adminUserMapper.insert(admin);
        // 日志不落明文口令：初始密码固定，需要知道的人从文档取，泄露日志不等于泄露账号
        log.info("已初始化超级管理员账号: admin（初始密码见 README，首次登录强制修改）");
    }
}
