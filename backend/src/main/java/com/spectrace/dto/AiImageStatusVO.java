package com.spectrace.dto;

import lombok.Data;

/**
 * AI 绘图可用性：后台据此提示配置缺口，不给管理员点开后才失败的体验。
 */
@Data
public class AiImageStatusVO {

    private boolean available;

    /** 后端总开关，便于把「被关闭」与「未配置」区分开 */
    private boolean enabled;

    /** 不可用原因（仅用于前端提示与排查，不含密钥等敏感信息） */
    private String reason;
}
