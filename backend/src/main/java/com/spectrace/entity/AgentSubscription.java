package com.spectrace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 系列订阅抓取（T88）：对指定系列按周期自动创建「系列发现」任务，
 * 发布季自动跟进，替代「人记得来点按钮」。
 */
@Data
@TableName("agent_subscription")
public class AgentSubscription {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** 一个系列只有一条订阅配置 */
    private Long seriesId;

    /** 抓取周期（月），1~24 */
    private Integer intervalMonths;

    /** 可选指定抓取模型（ai_model.id），空用默认模型 */
    private Long aiModelId;

    /** 1 启用 0 暂停 */
    private Integer enabled;

    /** 上次自动创建任务的时间；为空表示从未跑过，到点即触发 */
    private LocalDateTime lastRunAt;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
