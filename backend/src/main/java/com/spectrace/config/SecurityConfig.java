package com.spectrace.config;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

@Configuration
@EnableMethodSecurity
@RequiredArgsConstructor
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;

    /** 访客可直接访问的业务接口 / 接口文档 / 前端静态资源（一体化镜像由后端托管 SPA） */
    private static final String[] PUBLIC_PATHS = {
            "/",
            "/index.html",
            "/assets/**",
            // PWA 与浏览器自动抓取的根目录静态资源：白名单是精确匹配，漏一个就是 401（部署后 console 满屏红）
            "/manifest.webmanifest",
            "/sw.js",
            "/pwa-icon.svg",
            "/favicon.ico",
            "/api/auth/login",
            "/api/public/**",
            // MCP 端点由 McpApiKeyFilter 校验 API Key：Agent 没有后台账号，不能走 JWT 体系
            "/mcp",
            "/mcp/**",
            "/uploads/**",
            "/brands/**",
            "/devices/**",
            // 机型缩略图：与 /devices 前端路由分开，避免静态资源路径吞掉详情页路由
            "/device-images/**",
            // history 路由下的前端页面直接访问时返回 SPA 壳，登录态由前端路由守卫控制
            "/compare",
            // 系列趋势访客前端路由；/history /evolution 是被前端重定向的旧链接，保留放行让 SPA 壳完成跳转
            "/trends",
            "/history",
            "/evolution",
            "/admin/**",
            "/v3/api-docs/**",
            "/swagger-ui/**",
            "/swagger-ui.html",
            // 容器健康探针公开，metrics 仍需登录
            "/actuator/health",
            "/actuator/health/**",
            "/actuator/info",
            // 搜索引擎入口
            "/sitemap.xml",
            "/rss.xml",
            "/robots.txt",
            "/error"
    };

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)
                .cors(cors -> {
                })
                .headers(headers -> headers.frameOptions(frame -> frame.sameOrigin()))
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(reg -> reg
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers(PUBLIC_PATHS).permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(ex -> ex.authenticationEntryPoint((req, res, e) -> {
                    res.setStatus(401);
                    res.setContentType("application/json;charset=UTF-8");
                    res.getWriter().write("{\"code\":401,\"message\":\"未登录或登录已过期\"}");
                }));
        return http.build();
    }
}
