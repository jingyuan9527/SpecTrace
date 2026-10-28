package com.spectrace.dto;

import lombok.Data;

/**
 * AI 绘图结果：返回成品图地址与本次实际使用的模板/提示词，便于前端展示与后台对账。
 */
@Data
public class AiImageGenerateVO {

    private Long jobId;

    /** 可直接访问的图片地址（/uploads/...） */
    private String imageUrl;

    private Long promptId;

    private String promptName;

    /** 渲染后的完整提示词，前端可折叠展示，方便判断效果差是模板还是模型的问题 */
    private String promptText;

    private String model;

    private Long durationMs;
}
