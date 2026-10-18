package com.spectrace.task;

import com.spectrace.dto.AgentTaskPayload;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * 内存队列实现，供无 Redis 的本地开发环境使用。
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "spectrace.agent.queue", havingValue = "memory", matchIfMissing = true)
public class InMemoryTaskQueue implements TaskQueue {

    private final BlockingQueue<AgentTaskPayload> queue = new LinkedBlockingQueue<>();

    @Override
    public void push(AgentTaskPayload payload) {
        queue.offer(payload);
        log.info("任务入队(内存) taskId={} model={}", payload.getTaskId(), payload.getModelName());
    }

    @Override
    public AgentTaskPayload poll(Duration timeout) throws InterruptedException {
        return queue.poll(timeout.toMillis(), TimeUnit.MILLISECONDS);
    }
}
