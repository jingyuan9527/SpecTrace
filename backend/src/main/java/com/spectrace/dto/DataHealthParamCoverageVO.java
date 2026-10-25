package com.spectrace.dto;

import lombok.Data;

/**
 * 单个参数的全库覆盖情况：有该参数取值的机型数。
 */
@Data
public class DataHealthParamCoverageVO {

    private Long paramDefId;

    private String paramKey;

    private String paramName;

    private String groupName;

    /** 有取值的机型数（全库口径，含草稿） */
    private long deviceCount;
}
