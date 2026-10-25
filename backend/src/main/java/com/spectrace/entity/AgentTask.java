package com.spectrace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AgentScope 机型抓取任务。
 */
@Data
@TableName("agent_task")
public class AgentTask {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String modelName;

    private String brandName;

    /** 指定使用的 AI 模型（ai_model.id），为空则用默认模型 */
    private Long aiModelId;

    /** DEVICE_DETAIL（单机型参数抓取）/ SERIES_DISCOVERY（系列机型发现），存量数据无值按 DEVICE_DETAIL 处理 */
    private String taskType;

    /** 来源订阅（T88，可空）：非空表示该任务由系列订阅调度自动创建 */
    private Long subscriptionId;


    /** QUEUED / RUNNING / PENDING_REVIEW / APPROVED / DISCARDED / FAILED */
    private String status;

    private String sourceUrl;

    /** Agent 回传的结构化结果 JSON */
    private String result;

    private String errorMsg;

    private Long durationMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
