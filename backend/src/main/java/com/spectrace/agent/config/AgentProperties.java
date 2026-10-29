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

    /**
     * 单任务最长耗时（秒）。
     * 默认 300：慢模型一轮往返可达 10s+，46 项参数抽取约 15 轮，180s 偏紧；
     * 工具层有剩余预算闸门兜底，调大不会造成超时颗粒无收。
     */
    private int taskTimeoutSeconds = 300;

    /**
     * 单次模型请求超时（秒）：模型 API 挂死时快速失败，避免拖到任务整体超时。
     * 取值与工具层预算储备联动（储备 = 本值 + 15s 余量），调大会同步压缩可用采集时间。
     */
    private int modelTimeoutSeconds = 45;

    /** 连续失败达到该次数时告警，0 表示关闭 */
    private int alertThreshold = 3;

    /** 告警 webhook（钉钉/飞书/Slack 传入消息地址即可），留空只写 ERROR 日志 */
    private String alertWebhook = "";
}
