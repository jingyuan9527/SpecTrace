package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/**
 * 对比表格中的一行参数。values 与 CompareResultVO.devices 顺序一一对应。
 */
@Data
public class CompareParamVO {
    private Long paramDefId;
    private String paramKey;
    private String paramName;
    private String paramType;
    private String unit;
    private List<String> values;
    /** 各机型取值是否完全一致，前端据此决定是否高亮差异 */
    private boolean same;
}
