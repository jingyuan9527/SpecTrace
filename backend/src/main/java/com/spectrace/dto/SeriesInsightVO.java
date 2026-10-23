package com.spectrace.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 世代回顾 AI 文案。
 *
 * <p>字段语义与对比摘要一致：{@code available=false} 表示没配置可用模型，前端隐藏入口；
 * {@code failed=true} 表示本次生成失败且已降级。</p>
 */
@Data
public class SeriesInsightVO {

    private boolean available;

    private boolean failed;

    private boolean cached;

    private String summary;

    private List<String> points;

    private LocalDateTime generatedAt;
}
