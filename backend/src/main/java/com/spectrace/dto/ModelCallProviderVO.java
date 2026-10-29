package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/**
 * 供应商筛选项。对话连接为一组模型，绘图供应商固定一个模型。
 */
@Data
public class ModelCallProviderVO {

    /** CHAT / IMAGE */
    private String kind;

    private Long id;

    private String name;

    private List<ModelCallModelVO> models;
}
