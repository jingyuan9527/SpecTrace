package com.spectrace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AgentScope 抓取回调数据，进入正式的机型库前必须经管理员审核。
 */
@Data
@TableName("agent_pending_data")
public class AgentPendingData {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long taskId;

    private String modelName;

    private String brandName;

    /** {"paramKey": "value"} 结构，key 对应 param_def.param_key */
    private String paramsJson;

    private String sourceUrl;

    /** PENDING_REVIEW / APPROVED / DISCARDED */
    private String status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
