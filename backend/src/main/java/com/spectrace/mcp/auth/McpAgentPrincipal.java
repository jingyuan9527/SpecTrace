package com.spectrace.mcp.auth;

import java.util.Set;

/**
 * 一次 MCP 请求背后的 Agent 身份。
 *
 * <p>只从 API Key 解析得到，不接受请求体自报身份：工具入参里任何 "agentName" 都只作展示用途。</p>
 */
public record McpAgentPrincipal(Long agentId, String agentName, String role, Set<McpScope> scopes) {

    /**
     * operation_log 的主键是后台账号 id，Agent 没有账号。用 0 占位并在 adminName 上带 mcp: 前缀，
     * 后台日志页据此一眼区分人工操作与 Agent 操作，无需改表结构。
     */
    public static final Long AUDIT_ADMIN_ID = 0L;

    public boolean allows(McpScope scope) {
        return scopes.contains(scope);
    }

    public String auditName() {
        return "mcp:" + agentName;
    }
}
