package com.spectrace.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * 订阅排查视图：一屏看清「哪个系列的订阅、是否还指向存在的系列」。
 *
 * <p>系列删除后订阅曾是查不到的孤儿记录（订阅只能挂在系列详情下访问），
 * 这里把系列与品牌名一起带出来，缺 series 的记录直接标 {@code seriesMissing}。</p>
 */
@Data
public class SubscriptionOverviewVO {

    private Long id;

    private Long seriesId;

    /** 系列已删时为 null */
    private String seriesName;

    private Long brandId;

    private String brandName;

    /** 1 启用 0 暂停 */
    private Integer enabled;

    private Integer intervalMonths;

    private Long aiModelId;

    private LocalDateTime lastRunAt;

    /** 指向的系列已不存在：调度会在下个检查日自动停用，属于需要清理的历史遗留 */
    private Boolean seriesMissing;
}
