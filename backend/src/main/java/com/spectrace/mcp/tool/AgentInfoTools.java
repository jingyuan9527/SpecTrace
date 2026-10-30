package com.spectrace.mcp.tool;

import com.spectrace.mcp.auth.McpScope;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 接入自检：Agent 先确认自己是谁、能做什么，再决定调哪些工具。
 */
@Component
public class AgentInfoTools implements McpToolProvider {

    @Override
    public List<McpTool> tools() {
        return List.of(new McpTool(
                "spectrace_whoami",
                "查询当前 MCP 身份与权限",
                """
                        返回当前 API Key 对应的 Agent 名称、角色与已授权 scope 列表。
                        建议接入后先调用一次：看到 FORBIDDEN 时就能确认是缺 scope 而不是参数写错。
                        """,
                null,
                McpSchemas.object(McpSchemas.props()),
                (context, args) -> {
                    Map<String, Object> data = new LinkedHashMap<>();
                    data.put("agentId", context.principal().agentId());
                    data.put("agentName", context.principal().agentName());
                    data.put("role", context.principal().role());
                    data.put("scopes", context.principal().scopes().stream().map(McpScope::value).toList());
                    data.put("scopeDetails", context.principal().scopes().stream()
                            .map(scope -> Map.of("scope", scope.value(), "description", scope.description()))
                            .toList());
                    data.put("sessionId", context.sessionId());
                    return data;
                }));
    }
}
