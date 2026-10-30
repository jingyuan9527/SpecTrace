package com.spectrace.dto;

import lombok.Data;

/**
 * 创建或轮换后的返回：apiKey 是明文，仅此一次，之后只能看到前缀。
 */
@Data
public class McpAgentCreatedVO {

    private McpAgentVO agent;

    private String apiKey;

    private String warning;
}
