package com.spectrace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * AI 绘图历史。
 *
 * <p>成功与失败都落一条：失败记录是排查供应商 / Key / 尺寸问题的唯一线索，
 * 成功记录保存提示词快照——模板之后被改掉时，仍能解释某张图是怎么生成的。</p>
 */
@Data
@TableName("ai_image_job")
public class AiImageJob {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long providerId;

    private String providerName;

    private String model;

    private Long promptId;

    private String promptName;

    /** 渲染后的完整提示词快照 */
    private String promptText;

    /** 机型范围快照（逗号分隔） */
    private String deviceNames;

    /** 参数名快照（逗号分隔） */
    private String paramNames;

    /** SUCCESS / FAILED */
    private String status;

    private String imageUrl;

    private String errorMsg;

    private Long durationMs;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;
}
