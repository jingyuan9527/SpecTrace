package com.spectrace.agent;

/**
 * 采集线程内的任务上下文。
 *
 * <p>{@code web_search} / {@code fetch_page} 的参数签名会由 AgentScope 反射生成工具 Schema 暴露给大模型，
 * 不能为了传 taskId 而多加参数，因此用上下文让工具方法取到当前任务。
 *
 * <p>工具方法实际由 Agent 运行时调度到 Reactor boundedElastic 线程执行，ThreadLocal 在那里取不到
 * （曾因此把全部工具过程日志静默丢掉）。消费线程单线程串行，任何时刻最多只有一个任务在执行，
 * 用一个静态值兜底是安全的；若将来改多线程消费，必须改为显式传参。
 */
public final class TaskLogContext {

    private static final ThreadLocal<Long> CURRENT_TASK = new ThreadLocal<>();

    /** 当前在执行的任务 id，供调度线程上的工具方法兜底读取 */
    private static volatile Long ACTIVE_TASK;

    private TaskLogContext() {
    }

    public static void bind(Long taskId) {
        CURRENT_TASK.set(taskId);
        ACTIVE_TASK = taskId;
    }

    public static Long currentTaskId() {
        Long current = CURRENT_TASK.get();
        return current != null ? current : ACTIVE_TASK;
    }

    public static void clear() {
        CURRENT_TASK.remove();
        ACTIVE_TASK = null;
    }
}
