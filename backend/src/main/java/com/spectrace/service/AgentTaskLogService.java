package com.spectrace.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.spectrace.entity.AgentTaskLog;
import com.spectrace.mapper.AgentTaskLogMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 抓取任务日志读写。
 *
 * <p>写日志是辅助能力：任何写入失败都只降级为应用日志，绝不中断采集主流程，
 * 否则「看不到日志」会升级成「抓取失败」。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AgentTaskLogService {

    /** 单行上限，避免模型把整页正文回显导致日志表膨胀 */
    private static final int MAX_MESSAGE_LENGTH = 2000;

    private final AgentTaskLogMapper logMapper;

    public void info(Long taskId, String message) {
        append(taskId, "INFO", message);
    }

    public void warn(Long taskId, String message) {
        append(taskId, "WARN", message);
    }

    public void error(Long taskId, String message) {
        append(taskId, "ERROR", message);
    }

    public void append(Long taskId, String level, String message) {
        if (taskId == null || !StringUtils.hasText(message)) {
            return;
        }
        try {
            AgentTaskLog entry = new AgentTaskLog();
            entry.setTaskId(taskId);
            entry.setLevel(level);
            entry.setMessage(message.length() > MAX_MESSAGE_LENGTH
                    ? message.substring(0, MAX_MESSAGE_LENGTH) + "…"
                    : message);
            logMapper.insert(entry);
        } catch (Exception e) {
            log.warn("写入抓取日志失败: taskId={} level={} message={}", taskId, level, message, e);
        }
    }

    /** 按 id 升序返回，前端拿最后一条 id 做增量轮询即可。 */
    public List<AgentTaskLog> list(Long taskId, Long afterId) {
        var query = Wrappers.<AgentTaskLog>lambdaQuery()
                .eq(AgentTaskLog::getTaskId, taskId)
                .orderByAsc(AgentTaskLog::getId);
        if (afterId != null && afterId > 0) {
            query.gt(AgentTaskLog::getId, afterId);
        }
        return logMapper.selectList(query);
    }
}
