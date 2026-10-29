package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/**
 * 模型调用统计的筛选项：对话连接及其模型、绘图供应商。
 */
@Data
public class ModelCallDimensionsVO {

    private List<ModelCallProviderVO> providers;
}
