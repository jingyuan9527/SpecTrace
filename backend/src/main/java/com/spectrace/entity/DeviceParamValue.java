package com.spectrace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 机型参数值。value 统一以字符串存储，由前端按参数类型转换渲染。
 */
@Data
@TableName("device_param_value")
public class DeviceParamValue {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long deviceId;

    private Long paramDefId;

    private String paramValue;

    /** 该参数值的来源页面，人工录入或历史数据为空 */
    private String sourceUrl;

    /** 采集置信度 0~1，仅 Agent 抓取结果可携带 */
    private java.math.BigDecimal confidence;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
