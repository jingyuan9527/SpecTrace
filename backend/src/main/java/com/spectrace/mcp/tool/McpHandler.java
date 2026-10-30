package com.spectrace.mcp.tool;

/**
 * 工具处理逻辑。返回值只允许可 JSON 序列化的对象（Map/List/实体/DTO），由注册器统一包装成 MCP 结果。
 */
@FunctionalInterface
public interface McpHandler {

    Object handle(McpToolContext context, McpArgs args) throws Exception;
}
