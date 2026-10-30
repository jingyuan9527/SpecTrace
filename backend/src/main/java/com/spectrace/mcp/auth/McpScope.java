package com.spectrace.mcp.auth;

import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * MCP 工具授权范围。
 *
 * <p>按「资源:动作」拆分而不是只给一个角色：同一次接入里 Agent 可能只被允许补参数，
 * 却不应能删机型或改品牌。读写分离也让只读 Agent 的授权可以放心给。</p>
 */
public enum McpScope {

    DEVICE_READ("device:read", "查询机型及其参数、存储版本"),
    DEVICE_WRITE("device:write", "新增、修改、删除机型，批量上下架"),
    BRAND_READ("brand:read", "查询品牌"),
    BRAND_WRITE("brand:write", "新增、修改、删除品牌"),
    SERIES_READ("series:read", "查询系列"),
    SERIES_WRITE("series:write", "新增、修改、删除系列"),
    PARAM_READ("param:read", "查询参数分组与参数定义"),
    PARAM_WRITE("param:write", "新增、修改、删除参数分组与参数定义"),
    REVIEW_READ("review:read", "查询待审核数据"),
    REVIEW_WRITE("review:write", "审核通过或丢弃待审核数据"),
    FEEDBACK_READ("feedback:read", "查询访客纠错反馈"),
    FEEDBACK_WRITE("feedback:write", "处理访客纠错反馈"),
    STATS_READ("stats:read", "查询统计概览、操作日志与 MCP 调用日志");

    public static final String WILDCARD = "*";

    private final String value;

    private final String description;

    McpScope(String value, String description) {
        this.value = value;
        this.description = description;
    }

    public String value() {
        return value;
    }

    public String description() {
        return description;
    }

    /** 解析逗号分隔的 scope；未知值直接忽略，避免历史配置里残留的拼写让整个 Agent 无法认证 */
    public static Set<McpScope> parse(String raw) {
        if (raw == null || raw.isBlank()) {
            return Set.of();
        }
        String normalized = raw.trim();
        if (WILDCARD.equals(normalized)) {
            return all();
        }
        Set<String> accepted = Arrays.stream(values()).map(McpScope::value).collect(Collectors.toSet());
        return Arrays.stream(normalized.split(","))
                .map(String::trim)
                .filter(accepted::contains)
                .map(McpScope::of)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public static McpScope of(String value) {
        return Arrays.stream(values())
                .filter(scope -> scope.value.equals(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("未知的 MCP scope：" + value));
    }

    public static String join(Collection<McpScope> scopes) {
        return scopes.stream().map(McpScope::value).collect(Collectors.joining(","));
    }

    public static Set<McpScope> all() {
        return new LinkedHashSet<>(List.of(values()));
    }

    public static Set<McpScope> readOnlyDefaults() {
        return Arrays.stream(values())
                .filter(scope -> scope.name().endsWith("_READ"))
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** 编辑者：除「处理访客纠错」外全部读写；纠错对外发声，交给显式授权 */
    public static Set<McpScope> editorDefaults() {
        Set<McpScope> scopes = new LinkedHashSet<>(all());
        scopes.remove(FEEDBACK_WRITE);
        return scopes;
    }

    /** 按角色给默认 scope；自定义 scopes 优先于默认值 */
    public static Set<McpScope> defaultsOf(String role) {
        if (role == null) {
            return editorDefaults();
        }
        return switch (role.trim().toUpperCase()) {
            case "READ_ONLY" -> readOnlyDefaults();
            case "ADMIN" -> all();
            default -> editorDefaults();
        };
    }
}
