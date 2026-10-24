package com.spectrace.common;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.util.StringUtils;

/**
 * 客户端来源 IP 解析：反向代理下优先读 X-Forwarded-For 首个地址。
 *
 * <p>注意：X-Forwarded-For 可被客户端伪造，仅在部署于可信反代之后时才是真实来源；
 * 限流以它为维度在直连部署下可能被绕过，属已知取舍。
 */
public final class ClientIps {

    private ClientIps() {
    }

    public static String resolve(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (StringUtils.hasText(forwarded)) {
            return forwarded.split(",")[0].trim();
        }
        String remote = request.getRemoteAddr();
        return StringUtils.hasText(remote) ? remote : "unknown";
    }
}
