package com.spectrace.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 访客前端路由必须全部在 Spring Security 白名单内：
 * T31 的 /evolution 漏配导致生产直链 401，e2e 用 mock 拦不到真实安全链路，只能靠这里守住。
 */
class SecurityConfigTest {

    private static final List<String> VISITOR_ROUTES = List.of(
            "/", "/index.html", "/devices", "/devices/1", "/compare", "/trends",
            // 旧路由保留放行：分享链接直访时由 SPA 壳完成到 /trends 的重定向
            "/history", "/evolution");

    /** 浏览器 / PWA 在页面加载时自动请求的根目录文件，漏配同样只会得到 401。 */
    private static final List<String> STATIC_ASSETS = List.of(
            "/manifest.webmanifest", "/sw.js", "/pwa-icon.svg", "/favicon.ico");

    @Test
    @DisplayName("PUBLIC_PATHS 覆盖全部访客前端路由")
    void coversAllVisitorRoutes() throws Exception {
        List<String> publicPaths = publicPaths();

        for (String route : VISITOR_ROUTES) {
            boolean covered = publicPaths.stream().anyMatch(pattern ->
                    pattern.equals(route)
                            || (pattern.endsWith("/**") && route.startsWith(pattern.substring(0, pattern.length() - 3))));
            assertThat(covered).as("访客路由 %s 必须在 PUBLIC_PATHS 白名单内", route).isTrue();
        }
    }

    @Test
    @DisplayName("PUBLIC_PATHS 覆盖 PWA 与浏览器自动请求的静态资源")
    void coversPwaStaticAssets() throws Exception {
        List<String> publicPaths = publicPaths();

        for (String asset : STATIC_ASSETS) {
            assertThat(publicPaths).as("静态资源 %s 必须在 PUBLIC_PATHS 白名单内，否则部署后请求被鉴权拦截成 401", asset)
                    .contains(asset);
        }
    }

    private static List<String> publicPaths() throws Exception {
        Field field = SecurityConfig.class.getDeclaredField("PUBLIC_PATHS");
        field.setAccessible(true);
        return Arrays.asList((String[]) field.get(null));
    }
}
