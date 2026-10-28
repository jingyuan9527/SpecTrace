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
 * AI 绘图提示词模板。
 *
 * <p>模板正文里用 {@code {{变量}}} 占位，生成时由服务端按当前系列的机型与参数渲染，
 * 访客只能选择模板、不能提交任意提示词，避免公开接口被当成免费的绘图入口。</p>
 */
@Data
@TableName("ai_image_prompt")
public class AiImagePrompt {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    private String content;

    /** 1 = 系统内置模板：可编辑、可复制，但不允许删除 */
    private Integer builtin;

    /** 1 = 默认模板，全局唯一 */
    private Integer isDefault;

    private Integer enabled;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
