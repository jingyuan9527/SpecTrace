package com.spectrace.dto;

import lombok.Data;

import java.time.LocalDateTime;

/**
 * Tavily 本月用量：后台「AI 连接与模型」直接展示剩余额度，前端不用再做减法。
 *
 * <p>额度口径来自 Tavily `GET /usage` 的 account 段（当前计费周期），
 * limit 为空表示计划不限量或接口未返回额度。</p>
 */
@Data
public class TavilyUsageVO {

    /** Key 是否已配置；未配置时其余字段为空 */
    private boolean configured;

    /** 计划名，如 Free / Bootstrap / Business */
    private String plan;

    /** 本月已用积分（优先账户口径，缺失时回落到本 Key） */
    private Integer used;

    /** 本月额度；null 表示不限量或未知 */
    private Integer limit;

    /** 本月剩余积分 = limit - used，limit 为空时也为空 */
    private Integer remaining;

    /** 本 Key 已用（仅当与账户口径不同时返回，供排查多 Key 场景） */
    private Integer keyUsed;

    /** 本 Key 额度 */
    private Integer keyLimit;

    /** 用量数据的获取时间（命中缓存时为上次真实查询时间） */
    private LocalDateTime fetchedAt;

    /** 本次是否直接返回缓存（未请求 Tavily） */
    private boolean cached;

    /** 最近一次查询失败的原因；有缓存时仍返回缓存数据 */
    private String error;
}
