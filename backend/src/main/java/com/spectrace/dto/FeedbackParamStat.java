package com.spectrace.dto;

import lombok.Data;

/**
 * 被报错最多的参数统计行（T106）：数据质量薄弱点定位。
 */
@Data
public class FeedbackParamStat {

    /** 展示名优先 paramName（提交时快照），回落 paramKey */
    private String paramName;

    private String paramKey;

    private long count;
}
