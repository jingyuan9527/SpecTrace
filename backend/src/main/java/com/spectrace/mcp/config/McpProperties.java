package com.spectrace.mcp.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * MCP 服务配置。
 *
 * <p>默认开启但无 Agent 时所有请求 401：端点存在不等于可用，凭据必须显式创建。
 * 不需要该能力的环境用 {@code SPECTRACE_MCP_ENABLED=false} 彻底关闭。</p>
 */
@Data
@ConfigurationProperties(prefix = "spectrace.mcp")
public class McpProperties {

    private boolean enabled = true;

    private String endpoint = "/mcp";

    private String serverName = "spectrace";

    private String serverVersion = "1.0.0";

    /** 单页最多返回行数，防止 Agent 一次拉全库 */
    private int maxPageSize = 100;

    /** 调用日志中入参 JSON 的最大字符数，超出截断 */
    private int logArgumentsMaxLength = 2000;

    private Bootstrap bootstrap = new Bootstrap();

    @Data
    public static class Bootstrap {

        private String agentName = "";

        private String apiKey = "";

        private String role = "EDITOR";
    }
}
