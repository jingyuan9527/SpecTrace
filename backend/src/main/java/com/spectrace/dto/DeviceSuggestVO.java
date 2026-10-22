package com.spectrace.dto;

import lombok.Data;

/**
 * 搜索联想项的轻量视图，只带展示必需字段。
 */
@Data
public class DeviceSuggestVO {

    private Long id;

    private String name;

    private Long brandId;

    private String brandName;

    private String thumbnail;
}
