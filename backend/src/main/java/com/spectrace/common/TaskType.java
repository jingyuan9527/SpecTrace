package com.spectrace.common;

/**
 * 抓取任务类型。
 *
 * <p>存量数据无该列值，数据库默认 DEVICE_DETAIL，与既有单机型参数抓取行为保持一致。</p>
 */
public enum TaskType {

    /** 单机型参数抓取：结果进待审核库，审核后更新机型参数 */
    DEVICE_DETAIL,

    /** 系列机型发现：只产出机型清单，由管理员勾选后创建草稿机型档案 */
    SERIES_DISCOVERY;

    /** 容错解析：空或未知值回落到默认类型，避免脏数据让任务无法提交 */
    public static TaskType parse(String value) {
        if (value == null || value.isBlank()) {
            return DEVICE_DETAIL;
        }
        try {
            return TaskType.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return DEVICE_DETAIL;
        }
    }
}
