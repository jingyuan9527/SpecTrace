package com.spectrace.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDate;

/**
 * 访客行为统计：机型浏览量按天聚合（详情访问、对比发起），只增计数不改写。
 * 「热门机型」排序与数据健康度的补录优先级都以此为准，替代按发布时间倒序的旧口径。
 */
@Data
@TableName("device_view_daily")
public class DeviceViewDaily {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long deviceId;

    private LocalDate viewDate;

    private Integer pv;
}
