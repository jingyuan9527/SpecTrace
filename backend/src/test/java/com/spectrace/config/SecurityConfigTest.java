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
            "/", "/index.html", "/devices", "/devices/1", "/compare", "/history", "/evolution");

    @Test
    @DisplayName("PUBLIC_PATHS 覆盖全部访客前端路由")
    void coversAllVisitorRoutes() throws Exception {
        Field field = SecurityConfig.class.getDeclaredField("PUBLIC_PATHS");
        field.setAccessible(true);
        List<String> publicPaths = Arrays.asList((String[]) field.get(null));

        for (String route : VISITOR_ROUTES) {
            boolean covered = publicPaths.stream().anyMatch(pattern ->
                    pattern.equals(route)
                            || (pattern.endsWith("/**") && route.startsWith(pattern.substring(0, pattern.length() - 3))));
            assertThat(covered).as("访客路由 %s 必须在 PUBLIC_PATHS 白名单内", route).isTrue();
        }
    }
}
