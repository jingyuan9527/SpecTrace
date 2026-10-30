package com.spectrace.mcp.auth;

/**
 * API Key 缺失、格式错误、已停用或不匹配。
 *
 * <p>对外只回一句统一提示：区分「Key 不存在」与「Key 已停用」等于给暴力枚举提供判据。</p>
 */
public class McpAuthenticationException extends RuntimeException {

    public McpAuthenticationException(String message) {
        super(message);
    }
}
