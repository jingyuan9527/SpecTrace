package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/** 生成世代回顾文案的入参。 */
@Data
public class SeriesInsightRequest {

    /** 当前可见世代对应的机型；为空表示整条系列 */
    private List<Long> deviceIds;

    /** 当前勾选的参数；为空则后端按全系列机型返回，无参数时不生成 */
    private List<Long> paramDefIds;
}
