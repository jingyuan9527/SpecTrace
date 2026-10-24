package com.spectrace.agent;

/**
 * 采集线程内的任务上下文。
 *
 * <p>{@code web_search} / {@code fetch_page} 的参数签名会由 AgentScope 反射生成工具 Schema 暴露给大模型，
 * 不能为了传 taskId 而多加参数，因此用 ThreadLocal 让工具方法取到当前任务。
 * 采集 worker 单线程串行消费任务，同一线程内不会串任务；若将来改多线程消费，需改为显式传参。
 */
public final class TaskLogContext {

    private static final ThreadLocal<Long> CURRENT_TASK = new ThreadLocal<>();

    private TaskLogContext() {
    }

    public static void bind(Long taskId) {
        CURRENT_TASK.set(taskId);
    }

    public static Long currentTaskId() {
        return CURRENT_TASK.get();
    }

    public static void clear() {
        CURRENT_TASK.remove();
    }
}
