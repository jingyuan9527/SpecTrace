package com.spectrace.service;

import com.spectrace.common.TaskStatus;
import com.spectrace.entity.AgentTask;
import com.spectrace.mapper.AgentTaskMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * 抓取任务状态的唯一写入口。
 *
 * <p>任务状态被三条链路改写：Agent 消费线程（开始/回调）、人工重试与重启恢复、审核通过或丢弃。
 * 收敛到一处是为了让「状态变了但 updated_at 没变」「error 被空值覆盖」这类问题只可能出现在一个地方。</p>
 */
@Service
@RequiredArgsConstructor
public class AgentTaskStatusService {

    private final AgentTaskMapper taskMapper;

    /** 任务不存在时静默返回：调用方多为「顺带同步状态」，不该因为目标已删而中断主流程。 */
    public void updateStatus(Long taskId, TaskStatus status, String error) {
        if (taskId == null) {
            return;
        }
        AgentTask task = taskMapper.selectById(taskId);
        if (task == null) {
            return;
        }
        task.setStatus(status.name());
        if (error != null) {
            task.setErrorMsg(error);
        }
        task.setUpdatedAt(LocalDateTime.now());
        taskMapper.updateById(task);
    }

    /** 标记任务为抓取中（内嵌 Agent 开始执行时调用，便于后台展示进度）。 */
    public void markRunning(Long taskId) {
        updateStatus(taskId, TaskStatus.RUNNING, null);
    }
}
