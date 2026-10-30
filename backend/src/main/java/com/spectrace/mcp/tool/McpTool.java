package com.spectrace.mcp.tool;

import com.spectrace.mcp.auth.McpScope;
import io.modelcontextprotocol.spec.McpSchema;

import java.util.List;

/**
 * 一个可被 Agent 调用的工具定义。
 *
 * <p>工具是声明式的：名称、说明、schema 与所需 scope 集中在一处，注册器只负责统一鉴权、
 * 异常映射与审计，避免每个工具各写一套 try-catch。requiredScope 为 null 表示「已认证即可用」，
 * 仅用于 whoami 这类自检工具。</p>
 */
public record McpTool(String name, String title, String description, McpScope requiredScope,
                      McpSchema.JsonSchema inputSchema, McpHandler handler) {

    public McpTool {
        if (name == null || !name.matches("[a-z][a-z0-9_]{1,63}")) {
            throw new IllegalArgumentException("MCP 工具名必须是 2~64 位小写字母/数字/下划线：" + name);
        }
        if (description == null || description.isBlank()) {
            throw new IllegalArgumentException("MCP 工具必须提供描述：" + name);
        }
    }

    /** 工具声明顺序即返回顺序，便于客户端展示与排查 */
    public static List<McpTool> list(McpTool... tools) {
        return List.of(tools);
    }
}
