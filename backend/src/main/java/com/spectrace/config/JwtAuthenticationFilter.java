package com.spectrace.config;

import com.spectrace.entity.AdminUser;
import com.spectrace.mapper.AdminUserMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final AdminUserMapper adminUserMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (StringUtils.hasText(header) && header.startsWith("Bearer ")) {
            try {
                Claims claims = jwtUtil.parse(header.substring(7));
                Number uid = claims.get("uid", Number.class);
                // 每请求回查账号：禁用 / 删除 / 角色变更即时生效，不信任 token 里的旧 claim
                AdminUser user = uid == null ? null : adminUserMapper.selectById(uid.longValue());
                if (user != null && user.getStatus() != null && user.getStatus() == 1
                        // 强制改密未完成：仅放行认证相关接口，其余视为未登录，
                        // 否则持 token 不改密也能调全部管理接口，强制改密形同虚设
                        && !(isMustChangePassword(user) && !request.getRequestURI().startsWith("/api/auth/"))) {
                    var auth = new UsernamePasswordAuthenticationToken(
                            user.getUsername(), null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + user.getRole())));
                    auth.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
                    SecurityContextHolder.getContext().setAuthentication(auth);
                    request.setAttribute("adminId", user.getId());
                    request.setAttribute("adminName", user.getUsername());
                }
            } catch (Exception ignored) {
                // token 非法/过期，交由后续鉴权拒绝
            }
        }
        chain.doFilter(request, response);
    }

    private boolean isMustChangePassword(AdminUser user) {
        return user.getMustChangePassword() != null && user.getMustChangePassword() == 1;
    }
}
