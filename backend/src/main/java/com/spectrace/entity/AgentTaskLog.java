package com.spectrace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 抓取任务过程日志。
 *
 * <p>采集过程（搜索、抓网页、LLM 抽取）每完成一步追加一行，后台任务详情据此展示进度；
 * 单独建表而不是塞进 agent_task 的一列，是因为任务是「流式追加」的，单列写入会互相覆盖。
 */
@Data
@TableName("agent_task_log")
public class AgentTaskLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    /** INFO / WARN / ERROR */
    private String level;

    private String message;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
