package com.spectrace.agent.worker;

import com.spectrace.agent.config.AgentProperties;
import com.spectrace.agent.service.CollectService;
import com.spectrace.dto.AgentCallbackRequest;
import com.spectrace.dto.AgentTaskPayload;
import com.spectrace.dto.ResolvedModel;
import com.spectrace.entity.ParamDef;
import com.spectrace.service.AgentTaskService;
import com.spectrace.service.AiConfigService;
import com.spectrace.service.ParamService;
import com.spectrace.task.TaskQueue;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.List;

/**
 * 内嵌采集 Agent 工作线程：随应用启动，常驻从 {@link TaskQueue} 消费抓取任务并执行。
 *
 * <p>与业务后端同进程，直接调用 {@link AgentTaskService} / {@link AiConfigService} / {@link ParamService}，
 * 无需 HTTP 往返；结果直接写入待审核库。队列实现（内存 / Redis）由 {@code spectrace.agent.queue} 决定。
 *
 * <p>可通过 {@code spectrace.agent.enabled=false} 关闭；{@code --once} 仅处理一个任务，便于联调。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectorWorker implements ApplicationRunner {

    private final TaskQueue taskQueue;
    private final CollectService collectService;
    private final AgentTaskService agentTaskService;
    private final AiConfigService aiConfigService;
    private final ParamService paramService;
    private final AgentProperties props;

    @Override
    public void run(ApplicationArguments args) {
        if (!props.isEnabled()) {
            log.info("内嵌采集 Agent 未启用（spectrace.agent.enabled=false）");
            return;
        }
        boolean once = args.containsOption("once");
        Thread worker = new Thread(() -> loop(once), "collector-worker");
        worker.setDaemon(true);
        worker.start();
        log.info("内嵌采集 Agent 已启动，任务消费线程就绪（once={}）", once);
    }

    private void loop(boolean once) {
        while (!Thread.currentThread().isInterrupted()) {
            AgentTaskPayload payload;
            try {
                payload = taskQueue.poll(Duration.ofSeconds(props.getPollIntervalSeconds()));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                log.error("拉取任务失败：{}", e.getMessage());
                continue;
            }
            if (payload == null) {
                if (once) {
                    log.info("当前无任务，停止消费");
                    return;
                }
                continue;
            }
            handle(payload);
            if (once) {
                log.info("已处理一个任务，停止消费");
                return;
            }
        }
    }

    private void handle(AgentTaskPayload payload) {
        long start = System.currentTimeMillis();
        agentTaskService.markRunning(payload.getTaskId());

        List<ParamDef> schemaDefs = paramService.listDefs(null, 1);

        ResolvedModel aiConfig;
        try {
            // 每任务解析一次，后台改完「AI 配置」下个任务即生效，无需重启
            aiConfig = aiConfigService.resolve(payload.getAiModelId());
            log.info("开始采集 taskId={} model={}（provider={} model={}）",
                    payload.getTaskId(), payload.getModelName(), aiConfig.getProvider(), aiConfig.getModel());
        } catch (Exception e) {
            aiConfig = null;
            log.warn("解析 AI 配置失败 taskId={}：{}", payload.getTaskId(), e.getMessage());
        }

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
            agentTaskService.handleCallback(result);
            log.info("采集完成 taskId={} status={}", payload.getTaskId(), result.getStatus());
        } catch (Exception e) {
            log.error("写回采集结果失败 taskId={}", payload.getTaskId(), e);
        }
    }
}
