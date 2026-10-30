package com.spectrace.mcp.tool;

/**
 * 统一分页入参：Agent 传再大的 size 也压到配置上限，避免一次调用把整库拉进上下文。
 */
public record McpPageQuery(long current, long size) {

    public static McpPageQuery of(McpArgs args, int maxPageSize) {
        Long currentArg = args.optLong("current");
        Long sizeArg = args.optLong("size");
        long current = currentArg == null ? 1L : Math.max(1L, currentArg);
        long size = sizeArg == null ? 10L : Math.min(Math.max(1L, sizeArg), Math.max(1, maxPageSize));
        return new McpPageQuery(current, size);
    }
}
