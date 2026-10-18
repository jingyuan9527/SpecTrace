package com.spectrace.agent.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 内嵌采集 Agent 的运行时配置。
 *
 * <p>以 {@code spectrace.agent} 前缀绑定；队列相关配置（queue / queue-key）由业务侧维护，
 * 此处只关注采集本身。字段均可由同名环境变量覆盖。
 */
@Data
@ConfigurationProperties(prefix = "spectrace.agent")
public class AgentProperties {

    /** 是否启用内嵌采集 Agent（随应用启动常驻消费任务） */
    private boolean enabled = true;

    /** HarnessAgent 工作区目录（persona 与运行期会话数据的落盘位置） */
    private String workspace = ".agentscope/workspace";

    /** 阻塞等待任务的超时（秒），超时无任务则空转一轮 */
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
