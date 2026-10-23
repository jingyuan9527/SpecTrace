package com.spectrace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableLogic;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 产品线（系列）：人工维护的归类维度，用于「世代回顾」按代际查看参数走势。
 *
 * <p>同一品牌下系列名唯一；机型可空，未归类机型照常展示，只是不进世代回顾。</p>
 */
@Data
@TableName("device_series")
public class DeviceSeries {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long brandId;

    private String name;

    private Integer sortOrder;

    /** 1 启用 0 停用 */
    private Integer status;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;

    @TableLogic
    private Integer deleted;
}
