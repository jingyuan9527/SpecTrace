package com.spectrace.dto;

import lombok.Data;

/**
 * 访客端可选的提示词模板（只含选择与预览所需字段）。
 */
@Data
public class AiImagePromptOptionVO {

    private Long id;

    private String name;

    /** 模板正文（含 {{变量}}），前端用于预览本次会带上哪些数据 */
    private String content;

    private Integer isDefault;
}
