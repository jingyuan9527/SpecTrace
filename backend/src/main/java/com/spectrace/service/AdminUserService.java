package com.spectrace.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.spectrace.common.BizException;
import com.spectrace.entity.AdminUser;
import com.spectrace.mapper.AdminUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@RequiredArgsConstructor
public class AdminUserService {

    private final AdminUserMapper adminUserMapper;
    private final PasswordEncoder passwordEncoder;

    public IPage<AdminUser> page(long current, long size) {
        return adminUserMapper.selectPage(new Page<>(current, size),
                Wrappers.<AdminUser>lambdaQuery().orderByAsc(AdminUser::getId));
    }

    public AdminUser create(Map<String, String> body) {
        String username = body.get("username");
        String password = body.get("password");
        if (username == null || username.isBlank() || password == null || password.isBlank()) {
            throw new BizException("用户名和密码不能为空");
        }
        Long exists = adminUserMapper.selectCount(
                Wrappers.<AdminUser>lambdaQuery().eq(AdminUser::getUsername, username));
        if (exists != null && exists > 0) {
            throw new BizException("用户名已存在");
        }
        AdminUser user = new AdminUser();
        user.setUsername(username);
        user.setPassword(passwordEncoder.encode(password));
        user.setNickname(body.getOrDefault("nickname", username));
        user.setRole(body.getOrDefault("role", "ADMIN"));
        user.setStatus(1);
        // 初始密码由管理员代设，只有本人知道何时该换成自己的，首登强制修改
        user.setMustChangePassword(1);
        adminUserMapper.insert(user);
        return user;
    }

    public void updateRole(Long id, String role) {
        AdminUser user = adminUserMapper.selectById(id);
        if (user == null) {
            throw new BizException("账号不存在");
        }
        user.setRole(role);
        adminUserMapper.updateById(user);
    }

    public void toggleStatus(Long id) {
        AdminUser user = adminUserMapper.selectById(id);
        if (user == null) {
            throw new BizException("账号不存在");
        }
        user.setStatus(user.getStatus() != null && user.getStatus() == 1 ? 0 : 1);
        adminUserMapper.updateById(user);
    }

    public void resetPassword(Long id, String newPassword) {
        AdminUser user = adminUserMapper.selectById(id);
        if (user == null) {
            throw new BizException("账号不存在");
        }
        user.setPassword(passwordEncoder.encode(newPassword));
        // 重置后的密码经手过第三方（管理员），必须由本人再改一次
        user.setMustChangePassword(1);
        adminUserMapper.updateById(user);
    }

    public void delete(Long id) {
        adminUserMapper.deleteById(id);
    }
}
