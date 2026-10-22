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
 * 访客提交的参数纠错反馈。
 *
 * <p>机型名与参数名在提交时快照落库，避免后续参数定义改名/删除后历史反馈无法阅读。</p>
 */
@Data
@TableName("feedback")
public class Feedback {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long deviceId;

    private String deviceName;

    private Long paramDefId;

    private String paramKey;

    private String paramName;

    private String currentValue;

    private String suggestedValue;

    private String description;

    private String contact;

    /** OPEN / RESOLVED / IGNORED */
    private String status;

    private String handlerNote;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
