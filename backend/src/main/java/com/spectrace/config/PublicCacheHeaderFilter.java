package com.spectrace.config;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 公开 GET 接口的 HTTP 缓存头（T89）：进程内 Caffeine 缓存之外补齐浏览器 / CDN 层。
 *
 * <p>只对无状态查询加 5 分钟缓存；反馈进度查询必须实时（访客要立刻看到处理结果），
 * POST 天然不涉及。ETag 由 {@link org.springframework.web.filter.ShallowEtagHeaderFilter} 统一补，
 * 二次请求命中 304。</p>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 20)
public class PublicCacheHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if ("GET".equalsIgnoreCase(request.getMethod())
                && uri.startsWith("/api/public/")
                // 反馈进度必须实时可见；POST 天然不涉及
                && !uri.startsWith("/api/public/feedback")) {
            response.setHeader("Cache-Control", "public, max-age=300");
        }
        chain.doFilter(request, response);
    }
}
