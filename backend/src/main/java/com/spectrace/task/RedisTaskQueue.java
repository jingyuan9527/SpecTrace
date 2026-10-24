package com.spectrace.task;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.dto.AgentTaskPayload;
import lombok.RequiredArgsConstructor;
import lombok.SneakyThrows;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis List 队列实现，SpringBoot 生产任务、AgentScope 消费任务。
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "spectrace.agent.queue", havingValue = "redis")
public class RedisTaskQueue implements TaskQueue {

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    @Value("${spectrace.agent.queue-key}")
    private String queueKey;

    @Override
    @SneakyThrows
    public void push(AgentTaskPayload payload) {
        redisTemplate.opsForList().rightPush(queueKey, objectMapper.writeValueAsString(payload));
        log.info("任务入队(Redis) taskId={} model={}", payload.getTaskId(), payload.getModelName());
    }

    @Override
    @SneakyThrows
    public AgentTaskPayload poll(Duration timeout) {
        String json = redisTemplate.opsForList().leftPop(queueKey, timeout);
        return json == null ? null : objectMapper.readValue(json, AgentTaskPayload.class);
    }

    @Override
    public boolean isPersistent() {
        return true;
    }
}
