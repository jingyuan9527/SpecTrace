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
    /** Tavily 用量摘要（仅启用 Tavily 且查询成功时返回，如「Free：本月已用 37 / 1000 积分」） */
    private String tavilyUsage;
}
