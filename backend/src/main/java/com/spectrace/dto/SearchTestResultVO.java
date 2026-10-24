package com.spectrace.dto;

import lombok.Data;

/** 搜索源连通性测试结果：请求层失败也走 success=false 返回。 */
@Data
public class SearchTestResultVO {
    private boolean success;
    private String engine;
    private int count;
    private long latencyMs;
    private String error;
}
