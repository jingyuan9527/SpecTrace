package com.spectrace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.spectrace.common.PendingStatus;
import com.spectrace.common.TaskStatus;
import com.spectrace.dto.RetentionOverviewVO;
import com.spectrace.entity.AgentPendingData;
import com.spectrace.entity.AgentTask;
import com.spectrace.entity.AgentTaskLog;
import com.spectrace.entity.OperationLog;
import com.spectrace.mapper.AgentPendingDataMapper;
import com.spectrace.mapper.AgentTaskLogMapper;
import com.spectrace.mapper.AgentTaskMapper;
import com.spectrace.mapper.DeviceViewDailyMapper;
import com.spectrace.mapper.OperationLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 增长数据保留策略：SQLite 单文件库没有分区能力，只进不出的表会让查询与
 * {@code VACUUM INTO} 备份持续变慢，因此按天清理终态数据。
 *
 * <p>只删「终态」记录：抓取任务 FAILED / APPROVED / DISCARDED、待审核 APPROVED / DISCARDED；
 * 排队中与待审核中的数据永远不碰。删除终态任务前先删其过程日志，避免孤儿行。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DataRetentionService {

    private final AgentTaskMapper taskMapper;
    private final AgentTaskLogMapper taskLogMapper;
    private final AgentPendingDataMapper pendingMapper;
    private final OperationLogMapper operationLogMapper;
    private final DeviceViewDailyMapper viewMapper;

    @Value("${spectrace.retention.enabled:true}")
    private boolean enabled;

    @Value("${spectrace.retention.cron:0 0 4 * * *}")
    private String cron;

    @Value("${spectrace.retention.task-days:30}")
    private int taskDays;

    @Value("${spectrace.retention.pending-days:30}")
    private int pendingDays;

    @Value("${spectrace.retention.operation-log-days:90}")
    private int operationLogDays;

    @Value("${spectrace.retention.view-daily-days:90}")
    private int viewDailyDays;

    /** 默认每天 04:00 执行，排在 03:30 的备份之后：先备份再清理，删除的数据当天快照里仍有。 */
    @Scheduled(cron = "${spectrace.retention.cron:0 0 4 * * *}")
    public void scheduledCleanup() {
        if (!enabled) {
            log.debug("数据保留策略已关闭，跳过定时清理");
            return;
        }
        try {
            cleanup();
        } catch (Exception e) {
            log.error("定时清理失败，请检查数据库状态", e);
        }
    }

    /** 执行一次清理，返回各表删除行数。 */
    public Map<String, Long> cleanup() {
        Map<String, Long> removed = new LinkedHashMap<>();
        LocalDateTime now = LocalDateTime.now();

        List<Long> finishedTaskIds = taskMapper.selectList(Wrappers.<AgentTask>lambdaQuery()
                        .select(AgentTask::getId)
                        .in(AgentTask::getStatus, TaskStatus.FAILED.name(),
                                TaskStatus.APPROVED.name(), TaskStatus.DISCARDED.name())
                        .lt(AgentTask::getCreatedAt, now.minusDays(taskDays)))
                .stream().map(AgentTask::getId).toList();
        if (finishedTaskIds.isEmpty()) {
            removed.put("agent_task_log", 0L);
            removed.put("agent_task", 0L);
        } else {
            removed.put("agent_task_log", (long) taskLogMapper.delete(Wrappers.<AgentTaskLog>lambdaQuery()
                    .in(AgentTaskLog::getTaskId, finishedTaskIds)));
            removed.put("agent_task", (long) taskMapper.deleteByIds(finishedTaskIds));
        }

        removed.put("agent_pending_data", (long) pendingMapper.delete(Wrappers.<AgentPendingData>lambdaQuery()
                .in(AgentPendingData::getStatus, PendingStatus.APPROVED.name(), PendingStatus.DISCARDED.name())
                .lt(AgentPendingData::getCreatedAt, now.minusDays(pendingDays))));

        removed.put("operation_log", (long) operationLogMapper.delete(Wrappers.<OperationLog>lambdaQuery()
                .lt(OperationLog::getCreatedAt, now.minusDays(operationLogDays))));

        // 浏览量按天聚合天然有界，但同样按窗口滚动删除，跨年运行不膨胀
        removed.put("device_view_daily", (long) viewMapper.deleteBefore(now.toLocalDate().minusDays(viewDailyDays).toString()));

        log.info("保留策略清理完成: taskDays={} pendingDays={} operationLogDays={} viewDailyDays={} removed={}",
                taskDays, pendingDays, operationLogDays, viewDailyDays, removed);
        return removed;
    }

    /** 保留策略概览：配置与各增长表行数，供「系统运维」页展示。 */
    public RetentionOverviewVO overview() {
        RetentionOverviewVO vo = new RetentionOverviewVO();
        vo.setEnabled(enabled);
        vo.setCron(cron);
        vo.setTaskDays(taskDays);
        vo.setPendingDays(pendingDays);
        vo.setOperationLogDays(operationLogDays);
        vo.setViewDailyDays(viewDailyDays);
        vo.setNextRunAt(nextRunAt());
        Map<String, Long> counts = new LinkedHashMap<>();
        counts.put("agent_task", taskMapper.selectCount(null));
        counts.put("agent_task_log", taskLogMapper.selectCount(null));
        counts.put("agent_pending_data", pendingMapper.selectCount(null));
        counts.put("operation_log", operationLogMapper.selectCount(null));
        counts.put("device_view_daily", viewMapper.selectCount(null));
        vo.setCounts(counts);
        return vo;
    }

    private LocalDateTime nextRunAt() {
        if (!enabled) {
            return null;
        }
        try {
            return CronExpression.parse(cron).next(LocalDateTime.now());
        } catch (IllegalArgumentException e) {
            log.warn("保留策略 cron 表达式非法: {}", cron);
            return null;
        }
    }
}
