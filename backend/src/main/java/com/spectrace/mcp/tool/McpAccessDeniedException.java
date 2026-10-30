package com.spectrace.mcp.tool;

import com.spectrace.mcp.auth.McpScope;

/**
 * Agent 缺少工具所需 scope。与参数错误分开返回，避免 Agent 把「无权限」当成「参数写错」反复重试。
 */
public class McpAccessDeniedException extends RuntimeException {

    private final McpScope required;

    public McpAccessDeniedException(McpScope required, String agentName) {
        super("Agent「" + agentName + "」缺少权限：" + required.value());
        this.required = required;
    }

    public McpScope getRequired() {
        return required;
    }
}
