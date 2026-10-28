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
 * AI 绘图供应商：OpenAI 兼容 {@code /images/generations} 的一个可调用端点。
 *
 * <p>与对话模型 {@link AiConfig} 分开维护——绘图服务常与对话服务不同厂商、不同 Key，
 * 混在一张表里会让「默认模型」与「默认绘图模型」的语义互相污染。</p>
 */
@Data
@TableName("ai_image_provider")
public class AiImageProvider {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** OpenAI 兼容接口地址，如 https://api.openai.com/v1 */
    private String baseUrl;

    private String apiKey;

    /** 绘图模型，如 gpt-image-1 / dall-e-3 / wanx-v1 的兼容端点 */
    private String model;

    /** 出图尺寸，如 1024x1536（竖版海报） */
    private String size;

    /** 可选画质参数（部分端点不支持，留空则不传） */
    private String quality;

    private Integer enabled;

    private Integer isDefault;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;

    /** 非数据库字段：标识是否已配置 API Key，避免明文外泄 */
    @TableField(exist = false)
    private Boolean apiKeySet;
}
