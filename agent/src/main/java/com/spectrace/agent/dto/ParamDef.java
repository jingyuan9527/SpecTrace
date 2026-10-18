package com.spectrace.agent.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

/**
 * 参数定义（后端 {@code param_def} 的投影），用于约束 LLM 输出。
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ParamDef {

    private Long id;

    /** 稳定标识，LLM 输出的 params 键必须取自此处 */
    private String paramKey;

    private String paramName;

    private Long groupId;

    /** text / number / image / date / select */
    private String paramType;

    /** select 类型的枚举选项，JSON 数组字符串 */
    private String enumOptions;

    private String unit;

    private Integer sortOrder;

    private Integer enabled;
}
