package com.spectrace.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.agent.CrawlSession;
import com.spectrace.agent.TaskLogContext;
import com.spectrace.agent.config.AgentProperties;
import com.spectrace.service.AgentTaskLogService;
import io.agentscope.core.tool.Tool;
import io.agentscope.core.tool.ToolParam;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
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
     * 预算安全储备：剩余时间低于该值时工具拒绝执行，逼模型立即交卷。
     * 取值需覆盖一次慢模型往返（约 10~15s）加余量，否则模型收到「交卷」指令后还没输出完就被外层超时杀掉。
     */
    private static final long BUDGET_RESERVE_MS = 25_000;

    private final AgentProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;
    private final AgentTaskLogService taskLogService;

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
        String json;
        try {
            json = props.getSerperApiKey() != null && !props.getSerperApiKey().isBlank()
                    ? searchSerper(query)
                    : searchDuckDuckGo(query);
        } catch (Exception e) {
            log.warn("搜索失败 query={}: {}", query, e.getMessage());
            taskLog("WARN", "搜索失败：" + query + "，" + e.getMessage());
            return "[]";
        }
        taskLog("INFO", "搜索「" + query + "」返回 " + resultCount(json) + " 条结果（"
                + (System.currentTimeMillis() - start) + " ms）");
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
        if (remainMs >= BUDGET_RESERVE_MS) {
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

    private int resultCount(String json) {
        try {
            JsonNode node = objectMapper.readTree(json);
            return node.isArray() ? node.size() : 0;
        } catch (Exception e) {
            return 0;
        }
    }

    private String searchSerper(String query) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("q", query);
        body.put("num", props.getSearchResultLimit());

        JsonNode data = restClientBuilder.build()
                .post()
                .uri("https://google.serper.dev/search")
                .header("X-API-KEY", props.getSerperApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);

        List<Map<String, Object>> results = new ArrayList<>();
        if (data != null) {
            for (JsonNode item : data.path("organic")) {
                if (results.size() >= props.getSearchResultLimit()) {
                    break;
                }
                results.add(item(item.path("title").asText(null),
                        item.path("link").asText(null),
                        item.path("snippet").asText(null)));
            }
        }
        return toJson(results);
    }

    private String searchDuckDuckGo(String query) {
        String url = "https://html.duckduckgo.com/html/?q="
                + URLEncoder.encode(query, StandardCharsets.UTF_8);

        List<Map<String, Object>> results = new ArrayList<>();
        try {
            Document doc = Jsoup.connect(url).userAgent(UA).timeout(20000).get();
            Elements links = doc.select("a.result__a");
            for (Element link : links) {
                if (results.size() >= props.getSearchResultLimit()) {
                    break;
                }
                results.add(item(link.text(), decodeDuckDuckGoUrl(link.attr("href")), ""));
            }
        } catch (Exception e) {
            log.warn("DuckDuckGo 搜索失败 query={}: {}", query, e.getMessage());
        }
        return toJson(results);
    }

    private String toJson(List<Map<String, Object>> results) {
        try {
            return objectMapper.writeValueAsString(results);
        } catch (Exception e) {
            return "[]";
        }
    }

    private Map<String, Object> item(String title, String url, String snippet) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("url", url);
        m.put("snippet", snippet);
        return m;
    }

    /** DuckDuckGo 结果链接为跳转地址，尽量还原真实目标 URL。 */
    private String decodeDuckDuckGoUrl(String href) {
        if (href == null) {
            return null;
        }
        int idx = href.indexOf("uddg=");
        if (idx < 0) {
            return href;
        }
        String encoded = href.substring(idx + "uddg=".length());
        int amp = encoded.indexOf('&');
        if (amp >= 0) {
            encoded = encoded.substring(0, amp);
        }
        return URLDecoder.decode(encoded, StandardCharsets.UTF_8);
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
