package com.spectrace.dto;

import lombok.Data;

/**
 * 回传给 AgentScope 的任务载荷。
 */
@Data
public class AgentTaskPayload {
    private Long taskId;
    private String modelName;
    private String brandName;
    /** 该任务指定使用的 AI 模型，为空则由 Agent 用默认模型 */
    private Long aiModelId;

    /** TaskType 枚举名，消费端按类型分流到参数抓取 / 系列发现 */
    private String taskType;

    /**
     * 系列发现任务已收录的机型名（按发布时间从新到旧）。
     * 让模型只补「比最新已知机型更新」的机型，避免每次从零枚举整个系列导致迭代与时间耗尽。
     */
    private java.util.List<String> knownModels;
}
