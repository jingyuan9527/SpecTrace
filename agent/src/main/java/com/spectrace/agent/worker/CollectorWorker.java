package com.spectrace.agent.worker;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.agent.client.BackendClient;
import com.spectrace.agent.config.AgentProperties;
import com.spectrace.agent.dto.AgentCallbackRequest;
import com.spectrace.agent.dto.AgentTaskPayload;
import com.spectrace.agent.dto.ParamDef;
import com.spectrace.agent.dto.ResolvedModel;
import com.spectrace.agent.service.CollectService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * 采集 Agent 工作进程：启动后循环获取抓取任务并执行。
 *
 * <pre>
 *   Redis 模式：BLPOP 消费 spectrace:agent:task 队列
 *   HTTP  模式：轮询 GET /api/agent/tasks/pull
 * </pre>
 *
 * 执行完成后通过 /api/agent/callback 把结构化结果回传 SpringBoot（写入待审核库）。
 *
 * <p>用法：{@code java -jar app.jar}（常驻消费）；{@code java -jar app.jar --once}（处理一个任务后退出，便于联调）。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectorWorker implements ApplicationRunner {

    private final BackendClient backendClient;
    private final CollectService collectService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final AgentProperties props;

    private List<ParamDef> schemaDefs = List.of();
    private ResolvedModel aiConfig;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        boolean once = args.containsOption("once");

        refreshConfig(null);
        log.info("采集 Agent 启动，任务模式={}", props.getTaskMode());

        while (true) {
            AgentTaskPayload payload = nextTask();
            if (payload != null) {
                handle(payload);
                if (once) {
                    break;
                }
            } else if (once) {
                log.info("当前无任务，退出");
                break;
            }
        }
    }

    /**
     * 从后端刷新参数 Schema 与 AI 模型参数（后台可随时改，Agent 下一轮生效）。
     *
     * <p>modelId 为任务指定的模型，为空则后端使用默认模型。
     */
    private void refreshConfig(Long modelId) {
        schemaDefs = backendClient.fetchSchema();
        try {
            aiConfig = backendClient.fetchAiConfig(modelId);
            if (aiConfig != null) {
                log.info("已加载 AI 模型：provider={} model={} baseUrl={}",
                        aiConfig.getProvider(), aiConfig.getModel(), aiConfig.getBaseUrl());
            } else {
                log.warn("后端未返回 AI 配置，回退 .env 配置");
            }
        } catch (Exception e) {
            aiConfig = null;
            log.warn("拉取 AI 配置失败，回退 .env 配置：{}", e.getMessage());
        }
        log.info("已加载参数 Schema，共 {} 个字段", schemaDefs.size());
    }

    private AgentTaskPayload nextTask() throws Exception {
        if ("redis".equalsIgnoreCase(props.getTaskMode())) {
            String json = redisTemplate.opsForList()
                    .leftPop(props.getQueueKey(), Duration.ofSeconds(props.getPollIntervalSeconds()));
            if (json == null) {
                return null;
            }
            AgentTaskPayload payload = objectMapper.readValue(json, AgentTaskPayload.class);
            backendClient.markRunning(payload.getTaskId());
            return payload;
        }

        Optional<AgentTaskPayload> payload = backendClient.pullTask();
        if (payload.isEmpty()) {
            sleep(props.getPollIntervalSeconds());
            return null;
        }
        return payload.get();
    }

    private void handle(AgentTaskPayload payload) {
        long start = System.currentTimeMillis();
        // 每个任务执行前刷新配置，后台改动无需重启 Agent；
        // 任务若指定了模型（aiModelId），则使用该模型，否则用默认模型
        refreshConfig(payload.getAiModelId());
        log.info("开始采集 taskId={} model={}", payload.getTaskId(), payload.getModelName());

        AgentCallbackRequest result;
        try {
            result = collectService.collect(payload, schemaDefs, aiConfig);
        } catch (Exception e) {
            log.error("采集异常 taskId={}", payload.getTaskId(), e);
            result = new AgentCallbackRequest();
            result.setTaskId(payload.getTaskId());
            result.setModelName(payload.getModelName());
            result.setBrandName(payload.getBrandName());
            result.setStatus("FAILED");
            result.setError(e.getClass().getSimpleName() + ": " + e.getMessage());
        }
        result.setDurationMs(System.currentTimeMillis() - start);

        try {
            backendClient.callback(result);
            log.info("已回传结果 taskId={} status={}", payload.getTaskId(), result.getStatus());
        } catch (Exception e) {
            log.error("回传结果失败 taskId={}", payload.getTaskId(), e);
        }
    }

    private void sleep(int seconds) {
        try {
            Thread.sleep(Math.max(0, seconds) * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
