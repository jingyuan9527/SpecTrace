package com.spectrace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/**
 * 通用单值指标的日计数（T103），如对比发起次数。key+date 唯一累加。
 */
@Data
@TableName("metric_daily")
public class MetricDaily {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String metricKey;

    private LocalDate metricDate;

    private Long value;
}
