package com.spectrace.dto;

import lombok.Data;

/** 连通性测试结果：请求层面的失败也走 success=false 返回，前端据此展示延迟或错误详情。 */
@Data
public class ConnectivityTestVO {
    private boolean success;
    private long latencyMs;
    private String model;
    private String reply;
    private String error;
}
