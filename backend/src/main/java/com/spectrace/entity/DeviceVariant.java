package com.spectrace.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 机型的存储版本与官方发布价格。
 *
 * <p>同一机型可有多个版本（如 256GB / 512GB），价格默认取官方发布价；
 * currency 目前固定 CNY，priceType 预留 LAUNCH（发布价）/ CURRENT（当前价）。
 */
@Data
@TableName("device_variant")
public class DeviceVariant {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long deviceId;

    /** 存储容量，如 256GB、1TB */
    private String storage;

    /** 运行内存，如 12GB；无区分时为空串 */
    private String ram;

    private BigDecimal price;

    private String currency;

    /** LAUNCH 发布价 / CURRENT 当前价 */
    private String priceType;

    private Integer sortOrder;

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createdAt;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updatedAt;
}
