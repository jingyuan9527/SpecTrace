package com.spectrace.dto;

import lombok.Data;

/**
 * 模型筛选项。绘图供应商自身即模型，id 为空。
 */
@Data
public class ModelCallModelVO {

    private Long id;

    private String name;
}
