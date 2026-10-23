package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/** 生成对比摘要的入参。 */
@Data
public class CompareInsightRequest {

    private List<Long> ids;
}
