package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/**
 * 后台 AI 绘图请求（内部工具）。
 *
 * <p>只接收「选择项」而不是提示词正文：机型与参数由服务端按 ID 回库取数、模板按 ID 取库内正文，
 * 调用方因此无法把接口当成任意提示词的出图通道。</p>
 */
@Data
public class AiImageGenerateRequest {

    /** 系列 ID：机型范围与参数都从这个系列取 */
    private Long seriesId;

    /** 机型范围（系列内子集），空表示该系列全部已上架机型 */
    private List<Long> deviceIds;

    /** 参数范围，空表示默认参数（由服务端选数值型参数） */
    private List<Long> paramDefIds;

    /** 本次使用的提示词模板，空表示默认模板 */
    private Long promptId;

    /** 海报标题，最长 60 字，仅作为素材插入提示词 */
    private String title;
}
