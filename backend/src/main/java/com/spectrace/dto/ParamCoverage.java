package com.spectrace.dto;

import lombok.Data;

/**
 * 参数覆盖统计行：该参数在全库有取值的机型数。
 */
@Data
public class ParamCoverage {

    private Long paramDefId;

    private Long deviceCount;
}
