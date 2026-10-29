package com.spectrace.agent.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.agent.CrawlSession;
import com.spectrace.agent.TaskLogContext;
import com.spectrace.agent.config.AgentProperties;
import com.spectrace.service.AgentTaskLogService;
import com.spectrace.service.SearchConfigService;
import com.spectrace.service.WebSearchService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 采集 Agent 的工具集：搜索引擎 + 网页抓取。
 *
 * <p>以 {@code @Tool} 注解方法声明，AgentScope 反射注册后自动生成 JSON Schema：
 * 工具名取自 {@code name}，描述取自 {@code description}，参数取自 {@code @ToolParam}。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CollectTools {

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";
    private static final int MAX_CHARS = 8000;

    /**
     * 预算储备的余量：实际储备 = 单次模型超时 + 该余量。
     * 必须覆盖一次完整模型往返，否则模型收到「交卷」指令后还没输出完就被外层 300s 硬墙杀掉，
     * 已抓取的内容连同结果一起作废。
     */
    private static final long RESERVE_MARGIN_MS = 15_000;

    private final AgentProperties props;
    private final ObjectMapper objectMapper;
    private final AgentTaskLogService taskLogService;
    private final SearchConfigService searchConfigService;
    private final WebSearchService webSearchService;

    /**
     * 搜索机型参数相关的权威网页。
     *
     * @param query 搜索关键词，例如 "iPhone 17 参数 规格 官网"
     * @return 搜索结果 JSON 数组字符串，每项含 title / url / snippet
     */
    @Tool(name = "web_search",
            description = "搜索机型参数相关的权威网页，返回 JSON 数组字符串，每项含 title、url、snippet。",
            readOnly = true,
            concurrencySafe = true)
    public String webSearch(
            @ToolParam(name = "query", description = "搜索关键词，例如 \"iPhone 17 参数 规格 官网\"")
            String query) {
        String guard = budgetGuard();
        if (guard != null) {
            return guard;
        }
        String cacheKey = "q:" + query.trim().toLowerCase();
        String cached = CrawlSession.cached(cacheKey);
        if (cached != null) {
            taskLog("INFO", "搜索缓存命中（相同关键词，请勿重复搜索）：" + query);
            return cached;
        }
        sleepCrawlInterval();
        long start = System.currentTimeMillis();
        // 引擎路由与失败回落在 WebSearchService 内完成（后台「搜索源」配置，热生效）
        WebSearchService.Outcome outcome = webSearchService.search(searchConfigService.resolve(), query);
        taskLog("INFO", "搜索「" + query + "」经 " + outcome.engine() + " 返回 " + outcome.results().size()
                + " 条结果（" + (System.currentTimeMillis() - start) + " ms）");
        String json = toJson(outcome.results());
        CrawlSession.cache(cacheKey, json);
        return json;
    }

    /**
     * 加载网页并提取正文文本。
     *
     * @param url 目标网页地址
     * @return 网页正文纯文本（截断至 8000 字），失败时返回错误说明
     */
    @Tool(name = "fetch_page",
            description = "用 HTTP 请求加载网页并提取正文纯文本（截断至 8000 字），失败时返回错误说明。",
            readOnly = true,
            concurrencySafe = true)
    public String fetchPage(
            @ToolParam(name = "url", description = "目标网页地址")
            String url) {
        String guard = budgetGuard();
        if (guard != null) {
            return guard;
        }
        String host = hostOf(url);
        if (CrawlSession.domainFailed(host)) {
            taskLog("WARN", "域名熔断跳过抓取（此前已 401/403）：" + url);
            return "站点 " + host + " 此前已拒绝自动抓取（401/403），请更换其他来源，不要重试本站。";
        }
        String cacheKey = "u:" + url;
        String cached = CrawlSession.cached(cacheKey);
        if (cached != null) {
            taskLog("INFO", "页面缓存命中（该 URL 已抓取过）：" + url);
            return cached;
        }
        sleepCrawlInterval();
        long start = System.currentTimeMillis();
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(UA)
                    .timeout(30000)
                    .followRedirects(true)
                    // 反爬判定常查 Referer / Accept 系请求头，只带 UA 不够
                    .referrer("https://" + host + "/")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
                    .header("Accept-Language", "zh-CN,zh;q=0.9,en;q=0.8")
                    .get();
            doc.select("script, style, noscript, svg, head").remove();
            Element body = doc.body();
            String text = body != null ? body.wholeText() : doc.text();
            text = text.replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                    .replaceAll("\\n{3,}", "\n\n")
                    .trim();
            String content = text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
            taskLog("INFO", "抓取网页成功：" + url + "（正文 " + content.length() + " 字，"
                    + (System.currentTimeMillis() - start) + " ms）");
            CrawlSession.cache(cacheKey, content);
            return content;
        } catch (org.jsoup.HttpStatusException e) {
            log.warn("抓取网页失败 {}: HTTP {}", url, e.getStatusCode());
            if (e.getStatusCode() == 401 || e.getStatusCode() == 403) {
                // 定性拒绝：重试无意义，本任务内熔断整个域名，省下后续尝试与风控间隔
                CrawlSession.markDomainFailed(host);
                taskLog("WARN", "抓取网页失败：" + url + "（HTTP " + e.getStatusCode()
                        + "，站点拒绝自动抓取，已熔断该域名）");
                return "抓取失败：HTTP " + e.getStatusCode() + "，该站点拒绝自动抓取，请更换其他来源，不要重试本站。";
            }
            taskLog("WARN", "抓取网页失败：" + url + "，HTTP " + e.getStatusCode());
            return "抓取失败: HTTP " + e.getStatusCode();
        } catch (Exception e) {
            log.warn("抓取网页失败 {}: {}", url, e.getMessage());
            taskLog("WARN", "抓取网页失败：" + url + "，" + e.getMessage());
            return "抓取失败: " + e.getMessage();
        }
    }

    /**
     * 预算闸门：剩余时间不足时拒绝执行工具，返回给模型明确的交卷指令。
     * 模型想继续干活必须调用工具，工具拒绝就是最强的「交卷铃」。
     */
    private String budgetGuard() {
        long remainMs = props.getTaskTimeoutSeconds() * 1000L - CrawlSession.elapsedMs();
        long reserveMs = props.getModelTimeoutSeconds() * 1000L + RESERVE_MARGIN_MS;
        if (remainMs >= reserveMs) {
            return null;
        }
        long remainSec = Math.max(0, remainMs / 1000);
        taskLog("WARN", "剩余时间不足（" + remainSec + "s），工具停止执行，等待模型输出最终结果");
        return "剩余时间不足（约 " + remainSec + " 秒），请立即停止搜索与抓取，"
                + "基于已获取的信息输出最终 JSON；查不到的信息置 null，不要编造。";
    }

    private String hostOf(String url) {
        try {
            return java.net.URI.create(url).getHost();
        } catch (Exception e) {
            return null;
        }
    }

    /** 记录到当前采集任务；脱离采集线程（手工调用工具）时静默跳过，避免日志串到别的任务。 */
    private void taskLog(String level, String message) {
        Long taskId = TaskLogContext.currentTaskId();
        if (taskId == null) {
            return;
        }
        if ("WARN".equals(level)) {
            taskLogService.warn(taskId, message);
        } else {
            taskLogService.info(taskId, message);
        }
    }

    private String toJson(List<Map<String, Object>> results) {
        try {
            return objectMapper.writeValueAsString(results);
        } catch (Exception e) {
            return "[]";
        }
    }

    /** 抓取风控：每次外网请求前休眠固定间隔。 */
    private void sleepCrawlInterval() {
        try {
            Thread.sleep(Math.max(0, props.getCrawlIntervalSeconds()) * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
