package com.spectrace.dto;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 机型存储版本与价格，既用于详情/对比返回，也用于后台保存请求。
 */
@Data
public class DeviceVariantDTO {

    private Long id;

    /** 存储容量，如 256GB */
    private String storage;

    /** 运行内存，如 12GB；无区分时留空 */
    private String ram;

    /** 价格，单位由 currency 决定 */
    private BigDecimal price;

    /** 币种，默认 CNY */
    private String currency;

    /** LAUNCH 发布价 / CURRENT 当前价 */
    private String priceType;

    private Integer sortOrder;
}
