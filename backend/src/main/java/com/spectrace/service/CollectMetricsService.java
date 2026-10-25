package com.spectrace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.spectrace.common.TaskStatus;
import com.spectrace.dto.CollectDayPoint;
import com.spectrace.dto.CollectMetricsVO;
import com.spectrace.dto.ModelStatVO;
import com.spectrace.entity.AgentTask;
import com.spectrace.entity.AiConfig;
import com.spectrace.entity.AiModel;
import com.spectrace.entity.Device;
import com.spectrace.mapper.AgentTaskMapper;
import com.spectrace.mapper.AiConfigMapper;
import com.spectrace.mapper.AiModelMapper;
import com.spectrace.mapper.DeviceMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 采集链路监控聚合（T99）：全部为现成表的只读查询，供后台「运行监控」页使用。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CollectMetricsService {

    private final AgentTaskMapper taskMapper;
    private final DeviceMapper deviceMapper;
    private final AiModelMapper aiModelMapper;
    private final AiConfigMapper aiConfigMapper;

    /** 与采集任务同一个超时口径：耗时达到（上限 - 5s）即计为「贴近超时」 */
    @Value("${spectrace.agent.task-timeout-seconds:300}")
    private int taskTimeoutSeconds;

    public CollectMetricsVO overview(int windowDays) {
        CollectMetricsVO vo = new CollectMetricsVO();
        vo.setWindowDays(windowDays);
        LocalDateTime since = LocalDateTime.now().minusDays(windowDays);

        List<AgentTask> tasks = taskMapper.selectList(Wrappers.<AgentTask>lambdaQuery()
                .ge(AgentTask::getCreatedAt, since)
                .select(AgentTask::getStatus, AgentTask::getDurationMs, AgentTask::getCreatedAt));
        // 状态为字符串常量，select 后逐条归类；窗口内通常只有几百条，内存聚合足够
        List<Long> durations = new ArrayList<>();
        List<AgentTask> terminal = new ArrayList<>();
        for (AgentTask task : tasks) {
            String status = task.getStatus();
            if (TaskStatus.APPROVED.name().equals(status) || TaskStatus.DISCARDED.name().equals(status)
                    || TaskStatus.FAILED.name().equals(status)) {
                terminal.add(task);
                if (task.getDurationMs() != null) {
                    durations.add(task.getDurationMs());
                }
            }
        }

        vo.setTotal(terminal.size());
        vo.setApproved(count(terminal, TaskStatus.APPROVED.name()));
        vo.setDiscarded(count(terminal, TaskStatus.DISCARDED.name()));
        vo.setFailed(count(terminal, TaskStatus.FAILED.name()));
        long decided = vo.getApproved() + vo.getFailed();
        vo.setSuccessRate(decided == 0 ? null : (double) vo.getApproved() / decided);
        durations.sort(Long::compareTo);
        vo.setP50Ms(percentile(durations, 0.50));
        vo.setP95Ms(percentile(durations, 0.95));
        long nearThreshold = (long) (taskTimeoutSeconds - 5) * 1000;
        vo.setNearTimeout(durations.stream().filter(d -> d >= nearThreshold).count());
        vo.setDaily(dailyPoints(terminal, windowDays));
        vo.setModelStats(modelStats(tasks));

        vo.setQueued(taskMapper.selectCount(Wrappers.<AgentTask>lambdaQuery()
                .eq(AgentTask::getStatus, TaskStatus.QUEUED.name())));
        vo.setRunning(taskMapper.selectCount(Wrappers.<AgentTask>lambdaQuery()
                .eq(AgentTask::getStatus, TaskStatus.RUNNING.name())));
        vo.setPendingReview(taskMapper.selectCount(Wrappers.<AgentTask>lambdaQuery()
                .eq(AgentTask::getStatus, TaskStatus.PENDING_REVIEW.name())));
        vo.setImagePending(deviceMapper.selectCount(Wrappers.<Device>lambdaQuery()
                .isNotNull(Device::getThumbnailPendingUrl)));
        return vo;
    }

    /** 窗口内按模型聚合成功率与耗时；aiModelId 为空（默认模型历史任务）归入「默认模型」组。 */
    private List<ModelStatVO> modelStats(List<AgentTask> tasks) {
        record Acc(List<AgentTask> terminal, List<Long> durations) {
        }
        Map<Long, Acc> byModel = new java.util.LinkedHashMap<>();
        for (AgentTask task : tasks) {
            String status = task.getStatus();
            if (!(TaskStatus.APPROVED.name().equals(status) || TaskStatus.DISCARDED.name().equals(status)
                    || TaskStatus.FAILED.name().equals(status))) {
                continue;
            }
            Long modelId = task.getAiModelId() == null ? 0L : task.getAiModelId();
            Acc acc = byModel.computeIfAbsent(modelId, id -> new Acc(new ArrayList<>(), new ArrayList<>()));
            acc.terminal().add(task);
            if (task.getDurationMs() != null) {
                acc.durations().add(task.getDurationMs());
            }
        }
        if (byModel.isEmpty()) {
            return List.of();
        }
        Map<Long, String> modelNames = aiModelMapper.selectBatchIds(
                        byModel.keySet().stream().filter(id -> id != 0L).toList())
                .stream().collect(Collectors.toMap(AiModel::getId, AiModel::getModel));
        // AiModel 只存 configId：同批取回模型→连接映射，再一次反查全部连接名（连接数量有限）
        Map<Long, Long> modelToConfig = aiModelMapper.selectBatchIds(
                        byModel.keySet().stream().filter(id -> id != 0L).toList())
                .stream().collect(Collectors.toMap(AiModel::getId, m -> m.getConfigId() == null ? 0L : m.getConfigId()));
        Map<Long, String> configNames = aiConfigMapper.selectList(null).stream()
                .collect(Collectors.toMap(AiConfig::getId, AiConfig::getName));

        long nearThreshold = (long) (taskTimeoutSeconds - 5) * 1000;
        List<ModelStatVO> result = new ArrayList<>();
        byModel.forEach((modelId, acc) -> {
            ModelStatVO vo = new ModelStatVO();
            vo.setAiModelId(modelId == 0L ? null : modelId);
            String modelName = modelId == 0L ? null : modelNames.get(modelId);
            String configName = null;
            if (modelId != 0L) {
                Long configId = modelToConfig.get(modelId);
                if (configId != null && configId != 0L) {
                    configName = configNames.get(configId);
                }
            }
            vo.setLabel(configName == null
                    ? (modelName == null ? "默认模型" : modelName)
                    : configName + " / " + modelName);
            vo.setTotal(acc.terminal().size());
            vo.setApproved(acc.terminal().stream().filter(t -> TaskStatus.APPROVED.name().equals(t.getStatus())).count());
            vo.setFailed(acc.terminal().stream().filter(t -> TaskStatus.FAILED.name().equals(t.getStatus())).count());
            long decided = vo.getApproved() + vo.getFailed();
            vo.setSuccessRate(decided == 0 ? null : (double) vo.getApproved() / decided);
            List<Long> sorted = acc.durations().stream().sorted().toList();
            vo.setAvgMs(sorted.isEmpty() ? 0 : (long) sorted.stream().mapToLong(Long::longValue).average().orElse(0));
            vo.setP95Ms(percentile(sorted, 0.95));
            vo.setNearTimeout(sorted.stream().filter(d -> d >= nearThreshold).count());
            result.add(vo);
        });
        // 失败多的排前面：问题模型优先进入视线
        result.sort(java.util.Comparator.comparingLong(ModelStatVO::getFailed).reversed()
                .thenComparingLong(ModelStatVO::getTotal).reversed());
        return result;
    }

    private long count(List<AgentTask> tasks, String status) {
        return tasks.stream().filter(t -> status.equals(t.getStatus())).count();
    }

    private List<CollectDayPoint> dailyPoints(List<AgentTask> terminal, int windowDays) {
        // 从最早日期起逐天填点，无任务的天补零，保证折线连续
        LocalDate today = LocalDate.now();
        List<CollectDayPoint> points = new ArrayList<>();
        for (int offset = windowDays - 1; offset >= 0; offset--) {
            LocalDate day = today.minusDays(offset);
            CollectDayPoint point = new CollectDayPoint();
            point.setDate(day.toString());
            for (AgentTask task : terminal) {
                if (task.getCreatedAt() != null && task.getCreatedAt().toLocalDate().equals(day)) {
                    point.setTotal(point.getTotal() + 1);
                    switch (task.getStatus()) {
                        case "APPROVED" -> point.setApproved(point.getApproved() + 1);
                        case "DISCARDED" -> point.setDiscarded(point.getDiscarded() + 1);
                        default -> point.setFailed(point.getFailed() + 1);
                    }
                }
            }
            points.add(point);
        }
        return points;
    }

    private long percentile(List<Long> sorted, double p) {
        if (sorted.isEmpty()) {
            return 0;
        }
        int index = (int) Math.min(Math.floor(p * sorted.size()), sorted.size() - 1);
        return sorted.get(index);
    }
}
