package com.spectrace.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * 数据保留策略概览：配置、下次执行时间与各增长表当前行数。
 */
@Data
public class RetentionOverviewVO {

    private boolean enabled;

    private String cron;

    /** 终态抓取任务及其过程日志保留天数 */
    private int taskDays;

    /** 已处理（通过/丢弃）待审核数据保留天数 */
    private int pendingDays;

    /** 操作日志保留天数 */
    private int operationLogDays;

    /** 机型浏览量按天聚合的保留天数 */
    private int viewDailyDays;

    /** 访客搜索词聚合的保留天数 */
    private int searchLogDays;

    /** 模型调用明细的保留天数 */
    private int aiCallDays;

    /** MCP 工具调用日志的保留天数 */
    private int mcpCallDays;

    /** 下次定时清理时间，cron 非法或功能关闭时为 null */
    private LocalDateTime nextRunAt;

    /** 各增长表当前行数：agent_task / agent_task_log / agent_pending_data / operation_log / device_view_daily / search_log */
    private Map<String, Long> counts;
}
