package com.spectrace.mcp.tool;

/**
 * 入参校验失败。带上字段名，Agent 才能自行纠正后重试，而不是盲目重试同一个错误。
 */
public class McpArgumentException extends RuntimeException {

    private final String field;

    public McpArgumentException(String field, String message) {
        super(message);
        this.field = field;
    }

    public String getField() {
        return field;
    }
}
