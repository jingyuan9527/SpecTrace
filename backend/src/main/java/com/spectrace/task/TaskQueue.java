package com.spectrace.task;

import com.spectrace.dto.AgentTaskPayload;

/**
 * Agent 抓取任务队列抽象。默认内存实现用于本地开发，
 * 生产环境通过 spectrace.agent.queue=redis 切换到 Redis List。
 */
public interface TaskQueue {

    void push(AgentTaskPayload payload);

    /** 拉取一个任务，无任务时返回 null。 */
    AgentTaskPayload poll();
}
