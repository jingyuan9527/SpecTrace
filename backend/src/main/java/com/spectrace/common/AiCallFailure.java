package com.spectrace.common;

/**
 * 模型调用失败分类。
 *
 * <p>标签由后端统一给出，后台面板不再维护一份映射，新增分类时只需改这里。</p>
 */
public enum AiCallFailure {

    TIMEOUT("请求超时"),
    CONNECT("连接失败"),
    AUTH("认证失败"),
    RATE_LIMIT("触发限流"),
    CLIENT("请求参数错误"),
    SERVER("服务端错误"),
    EMPTY_RESPONSE("空返回"),
    INVALID_RESPONSE("返回格式异常"),
    CANCELLED("调用被取消"),
    UNKNOWN("其他错误");

    private final String label;

    AiCallFailure(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    /** 历史数据可能带未知分类，统一降级为「其他错误」而不是前端显示空标签。 */
    public static String labelOf(String name) {
        if (name == null || name.isBlank()) {
            return UNKNOWN.label;
        }
        try {
            return valueOf(name).label;
        } catch (IllegalArgumentException e) {
            return UNKNOWN.label;
        }
    }
}
