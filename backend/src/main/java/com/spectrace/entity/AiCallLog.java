package com.spectrace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 单次模型调用明细。
 *
 * <p>按调用发生时的供应商与模型落快照，而不是只存外键：连接或模型改名、删除后，
 * 历史统计仍能按当时的口径解释，不会因为一次重命名把趋势断成两截。</p>
 */
@Data
@TableName("ai_call_log")
public class AiCallLog {

    @TableId(type = IdType.AUTO)
    private Long id;

    /** AGENT_SCRAPE / AGENT_DISCOVERY / INSIGHT / IMAGE */
    private String scene;

    /** CHAT（对话连接）/ IMAGE（绘图供应商） */
    private String providerKind;

    private Long providerId;

    private String providerName;

    /** 绘图供应商自身固定一个模型，此处为空 */
    private Long modelId;

    private String modelName;

    private Integer success;

    /** HTTP 状态码；网络层失败时为空 */
    private Integer statusCode;

    private Long latencyMs;

    /** 失败分类，取值见 AiCallFailure；成功时为空 */
    private String errorType;

    private String errorMsg;

    private Integer promptTokens;

    private Integer completionTokens;

    /** 关联业务标识（抓取任务 id 等），便于从统计跳回具体任务排查 */
    private String bizId;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
