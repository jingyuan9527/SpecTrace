package com.spectrace.task;

import com.spectrace.dto.AgentTaskPayload;

import java.time.Duration;

/**
 * Agent 抓取任务队列抽象。默认内存实现无需外部服务，
 * 多实例部署可通过 spectrace.agent.queue=redis 切换到 Redis List。
 */
public interface TaskQueue {

    void push(AgentTaskPayload payload);

    /** 阻塞拉取一个任务，超时无任务返回 null。 */
    AgentTaskPayload poll(Duration timeout) throws InterruptedException;
}
