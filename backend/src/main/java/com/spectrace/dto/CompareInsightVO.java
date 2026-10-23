package com.spectrace.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 对比页 AI 差异摘要。
 *
 * <p>{@code available=false} 表示站点没配置可用模型，前端应隐藏整个入口；
 * {@code failed=true} 表示本次生成失败（已降级，前端只展示一行轻提示）。</p>
 */
@Data
public class CompareInsightVO {

    private boolean available;

    private boolean failed;

    /** 命中缓存，前端可据此少给一次「AI 正在生成」的心理预期 */
    private boolean cached;

    private String summary;

    private List<String> points;

    private LocalDateTime generatedAt;
}
