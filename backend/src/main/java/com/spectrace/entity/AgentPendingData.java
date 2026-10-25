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

    /**
     * AI 预审风险等级（非表字段，T86）：HIGH=含低置信度或单位冲突，MEDIUM=与库中现有值有差异，
     * PASS=全部参数高置信且无冲突，NORMAL=其余。仅待审核列表按需计算。
     */
    @TableField(exist = false)
    private String riskLevel;
}
