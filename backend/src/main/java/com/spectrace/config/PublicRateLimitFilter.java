package com.spectrace.config;

import com.spectrace.common.ClientIps;
import com.spectrace.common.MemoryRateLimiter;
import com.spectrace.common.MetricsCounter;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;

/**
 * 公开接口基础限流：单 IP 每分钟请求数上限，防止匿名低 craw 全库或制造慢查询。
 *
 * <p>这是兜底基线，feedback / insight 等写操作与模型调用有各自更严格的限流，二者叠加生效。
 * 阈值默认 120/min：正常访客一页几个请求绰绰有余，脚本爬取则会被明显减速。</p>
 */
@Component
@RequiredArgsConstructor
public class PublicRateLimitFilter extends OncePerRequestFilter {

    private final MemoryRateLimiter rateLimiter;
    private final MetricsCounter metricsCounter;

    @Value("${spectrace.public.rate-limit-qpm:120}")
    private int qpm;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String uri = request.getRequestURI();
        if (uri.startsWith("/api/public/") && !rateLimiter.allow("public:" + ClientIps.resolve(request), qpm, Duration.ofMinutes(1))) {
            // 429 按归一化路径（数字 id 折叠）计数，运维页可看哪些接口被限流（T105）
            metricsCounter.increment("429:" + uri.replaceAll("\\d+", "{id}"));
            response.setStatus(429);
            response.setContentType("application/json;charset=UTF-8");
            response.getWriter().write("{\"code\":429,\"message\":\"请求过于频繁，请稍后再试\"}");
            return;
        }
        chain.doFilter(request, response);
    }
}
