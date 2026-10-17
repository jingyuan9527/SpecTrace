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
 * AI 供应商连接。一个连接（provider + base_url + api_key）下可配置多个模型。
 */
@Data
@TableName("ai_config")
public class AiConfig {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** dashscope / openai / deepseek / moonshot / ollama ... */
    private String provider;

    /** OpenAI 兼容接口地址，如 https://dashscope.aliyuncs.com/compatible-mode/v1 */
    private String baseUrl;

    private String apiKey;

    private Integer enabled;

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
