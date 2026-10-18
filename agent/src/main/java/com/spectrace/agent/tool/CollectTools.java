package com.spectrace.agent.tool;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.agent.config.AgentProperties;
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

    private final AgentProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

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
        sleepCrawlInterval();
        try {
            if (props.getSerperApiKey() != null && !props.getSerperApiKey().isBlank()) {
                return searchSerper(query);
            }
            return searchDuckDuckGo(query);
        } catch (Exception e) {
            log.warn("搜索失败 query={}: {}", query, e.getMessage());
            return "[]";
        }
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
        sleepCrawlInterval();
        try {
            Document doc = Jsoup.connect(url)
                    .userAgent(UA)
                    .timeout(30000)
                    .followRedirects(true)
                    .get();
            doc.select("script, style, noscript, svg, head").remove();
            Element body = doc.body();
            String text = body != null ? body.wholeText() : doc.text();
            text = text.replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                    .replaceAll("\\n{3,}", "\n\n")
                    .trim();
            return text.length() > MAX_CHARS ? text.substring(0, MAX_CHARS) : text;
        } catch (Exception e) {
            log.warn("抓取网页失败 {}: {}", url, e.getMessage());
            return "抓取失败: " + e.getMessage();
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
