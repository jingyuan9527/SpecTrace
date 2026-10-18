package com.spectrace.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 采集 Agent 运行时配置，字段与资源文件中的环境变量一一对应。
 */
@Data
@ConfigurationProperties(prefix = "spectrace.agent")
public class AgentProperties {

    /** SpringBoot 业务后端地址 */
    private String backendUrl = "http://localhost:8080";

    /** 与后端 spectrace.agent.callback-token 保持一致 */
    private String token = "spectrace-agent-callback-token";

    /** HarnessAgent 工作区目录 */
    private String workspace = ".agentscope/workspace";

    /** 任务获取方式：http | redis */
    private String taskMode = "http";

    /** Redis 连接串，形如 redis://[:password@]host:port/db */
    private String redisUrl = "redis://127.0.0.1:6379/0";

    /** 任务队列键 */
    private String queueKey = "spectrace:agent:task";

    /** 轮询间隔（秒） */
    private int pollIntervalSeconds = 5;

    /** 回退用 DashScope API Key（后台未配置 AI 连接时生效） */
    private String dashscopeApiKey = "";

    /** 回退用模型名 */
    private String dashscopeModel = "qwen-max";

    /** Serper API Key，留空则使用 DuckDuckGo 免 key 抓取 */
    private String serperApiKey = "";

    /** 搜索结果条数上限 */
    private int searchResultLimit = 5;

    /** 每次外网请求间隔（秒），用于抓取风控 */
    private int crawlIntervalSeconds = 2;

    /** 单任务最长耗时（秒） */
    private int taskTimeoutSeconds = 180;
}
