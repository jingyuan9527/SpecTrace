package com.spectrace.common;

/**
 * Agent 抓取任务状态。
 */
public enum TaskStatus {
    /** 已入队，等待 AgentScope 消费 */
    QUEUED,
    /** AgentScope 正在抓取 */
    RUNNING,
    /** 抓取完成，结果已写入待审核库 */
    PENDING_REVIEW,
    /** 管理员审核通过，已迁移到正式机型库 */
    APPROVED,
    /** 管理员丢弃 */
    DISCARDED,
    /** 抓取失败（网络 / 解析异常） */
    FAILED
}
