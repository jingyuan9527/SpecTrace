package com.spectrace.config;

import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.filter.ShallowEtagHeaderFilter;

/**
 * 公开接口 HTTP 层缓存（T89）：ShallowETag 对响应体做内容指纹，
 * 浏览器 / CDN 二次请求带 If-None-Match 命中 304，SQLite 与带宽双省。
 */
@Configuration
public class WebCacheConfig {

    @Bean
    public FilterRegistrationBean<ShallowEtagHeaderFilter> publicEtagFilter() {
        FilterRegistrationBean<ShallowEtagHeaderFilter> registration =
                new FilterRegistrationBean<>(new ShallowEtagHeaderFilter());
        registration.addUrlPatterns("/api/public/*");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 21);
        registration.setName("publicEtagFilter");
        return registration;
    }
}
