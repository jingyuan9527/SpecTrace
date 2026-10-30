package com.spectrace.mcp.tool;

import com.spectrace.mcp.auth.McpAgentPrincipal;

/**
 * 工具执行上下文：身份、会话与审计口径都在这里，工具实现不再自己去找请求信息。
 */
public record McpToolContext(McpAgentPrincipal principal, String sessionId, String clientName) {

    /** 写操作统一用（0, mcp:xxx）落 operation_log，与后台人工操作可区分 */
    public Long auditAdminId() {
        return McpAgentPrincipal.AUDIT_ADMIN_ID;
    }

    public String auditAdminName() {
        return principal.auditName();
    }
}
