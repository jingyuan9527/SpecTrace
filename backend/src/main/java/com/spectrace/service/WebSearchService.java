package com.spectrace.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.agent.config.AgentProperties;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 搜索引擎统一入口：Tavily / SearXNG（自建）/ Serper / DuckDuckGo 四个后端，
 * 按后台「搜索源」配置路由，所选引擎失败时自动回落 DuckDuckGo。
 *
 * <p>采集工具（CollectTools）与搜索源测试接口共用这里；Tavily 与 SearXNG
 * 返回内容摘要（snippet），是 fetch_page 被反爬拒绝时模型手里的降级信息源。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class WebSearchService {

    private static final String UA =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private final AgentProperties props;
    private final ObjectMapper objectMapper;
    private final RestClient.Builder restClientBuilder;

    /** 后台搜索源配置的运行时视图。 */
    @Data
    public static class SearchRuntime {
        /** 启用的引擎（tavily/searxng/serper，按尝试顺序），空表示系统默认链 */
        private List<String> engines;
        private String tavilyApiKey;
        private String searxngBaseUrl;
        private String searxngToken;
        private String serperApiKey;
    }

    /** 一次搜索的结果：实际使用的引擎 + title/url/snippet 列表。 */
    public record Outcome(String engine, List<Map<String, Object>> results) {
    }

    /**
     * 按配置执行搜索：启用的引擎依次尝试（Tavily → SearXNG → Serper），全部失败回落 DuckDuckGo；
     * DuckDuckGo 也失败时返回空结果而非异常，调用方（采集工具）只负责记录，不需要为搜索失败做容错。
     * 未启用任何引擎时走默认链（有 Serper Key 用 Google，否则 DuckDuckGo）。
     */
    public Outcome search(SearchRuntime cfg, String query) {
        List<String> engines = cfg.getEngines() == null ? List.of() : cfg.getEngines();
        for (String engine : engines) {
            try {
                List<Map<String, Object>> results = switch (engine) {
                    case "tavily" -> searchTavily(cfg, query);
                    case "searxng" -> searchSearxng(cfg, query);
                    case "serper" -> searchSerper(cfg.getSerperApiKey(), query);
                    default -> null;
                };
                if (results != null) {
                    return new Outcome(engine, results);
                }
            } catch (Exception e) {
                log.warn("搜索引擎 {} 失败，尝试下一个: {}", engine, e.getMessage());
            }
        }
        if (engines.isEmpty()) {
            try {
                if (cfg.getSerperApiKey() != null && !cfg.getSerperApiKey().isBlank()) {
                    return new Outcome("serper", searchSerper(cfg.getSerperApiKey(), query));
                }
            } catch (Exception e) {
                log.warn("搜索引擎 serper 失败，尝试下一个: {}", e.getMessage());
            }
        }
        try {
            return new Outcome("duckduckgo", searchDuckDuckGo(query));
        } catch (Exception fallback) {
            log.warn("DuckDuckGo 搜索也失败: {}", fallback.getMessage());
            return new Outcome("none", List.of());
        }
    }

    /**
     * 查询 Tavily 用量（GET /usage，10 分钟限 10 次，只应在测试场景调用）。
     * 失败返回 null，不影响测试结论。
     */
    public String fetchTavilyUsage(String apiKey) {
        try {
            JsonNode data = restClientBuilder.build()
                    .get()
                    .uri("https://api.tavily.com/usage")
                    .header("Authorization", "Bearer " + apiKey)
                    .retrieve()
                    .body(JsonNode.class);
            if (data == null) {
                return null;
            }
            String plan = data.path("account").path("current_plan").asText("");
            int planUsage = data.path("account").path("plan_usage").asInt(-1);
            int planLimit = data.path("account").path("plan_limit").asInt(-1);
            int keyUsage = data.path("key").path("usage").asInt(-1);
            int keyLimit = data.path("key").path("limit").asInt(-1);
            StringBuilder sb = new StringBuilder();
            if (!plan.isBlank()) {
                sb.append(plan).append(" 计划：已用 ").append(Math.max(planUsage, 0));
                sb.append(planLimit >= 0 ? " / " + planLimit : "（不限量）");
            }
            if (keyUsage >= 0 && (keyLimit >= 0 || keyUsage != planUsage)) {
                if (!sb.isEmpty()) {
                    sb.append("；");
                }
                sb.append("本 Key：已用 ").append(keyUsage);
                sb.append(keyLimit >= 0 ? " / " + keyLimit : "");
            }
            return sb.isEmpty() ? null : sb.toString() + " 积分";
        } catch (Exception e) {
            log.warn("查询 Tavily 用量失败: {}", e.getMessage());
            return null;
        }
    }

    private List<Map<String, Object>> searchTavily(SearchRuntime cfg, String query) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("query", query);
        body.put("max_results", props.getSearchResultLimit());

        JsonNode data = restClientBuilder.build()
                .post()
                .uri("https://api.tavily.com/search")
                .header("Authorization", "Bearer " + cfg.getTavilyApiKey())
                .contentType(MediaType.APPLICATION_JSON)
                .body(body)
                .retrieve()
                .body(JsonNode.class);
        List<Map<String, Object>> results = new ArrayList<>();
        if (data != null) {
            for (JsonNode item : data.path("results")) {
                if (results.size() >= props.getSearchResultLimit()) {
                    break;
                }
                results.add(item(text(item, "title"), text(item, "url"), text(item, "content")));
            }
        }
        return results;
    }

    /** SearXNG JSON 接口：实例需开启 format=json；token 以查询参数传递（自建实例常见做法）。 */
    private List<Map<String, Object>> searchSearxng(SearchRuntime cfg, String query) throws Exception {
        StringBuilder url = new StringBuilder(cfg.getSearxngBaseUrl())
                .append("/search?format=json&q=")
                .append(URLEncoder.encode(query, StandardCharsets.UTF_8));
        if (cfg.getSearxngToken() != null && !cfg.getSearxngToken().isBlank()) {
            url.append("&token=").append(URLEncoder.encode(cfg.getSearxngToken(), StandardCharsets.UTF_8));
        }
        JsonNode data = restClientBuilder.build()
                .get()
                .uri(url.toString())
                .retrieve()
                .body(JsonNode.class);
        List<Map<String, Object>> results = new ArrayList<>();
        if (data != null) {
            for (JsonNode item : data.path("results")) {
                if (results.size() >= props.getSearchResultLimit()) {
                    break;
                }
                results.add(item(text(item, "title"), text(item, "url"), text(item, "content")));
            }
        }
        return results;
    }

    private List<Map<String, Object>> searchSerper(String apiKey, String query) throws Exception {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("q", query);
        body.put("num", props.getSearchResultLimit());

        JsonNode data = restClientBuilder.build()
                .post()
                .uri("https://google.serper.dev/search")
                .header("X-API-KEY", apiKey)
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
                results.add(item(text(item, "title"), text(item, "link"), text(item, "snippet")));
            }
        }
        return results;
    }

    private List<Map<String, Object>> searchDuckDuckGo(String query) throws Exception {
        String url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, StandardCharsets.UTF_8);
        Document doc = Jsoup.connect(url).userAgent(UA).timeout(20000).get();
        List<Map<String, Object>> results = new ArrayList<>();
        Elements links = doc.select("a.result__a");
        for (Element link : links) {
            if (results.size() >= props.getSearchResultLimit()) {
                break;
            }
            results.add(item(link.text(), decodeDuckDuckGoUrl(link.attr("href")), ""));
        }
        return results;
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

    private Map<String, Object> item(String title, String url, String snippet) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("title", title);
        m.put("url", url);
        m.put("snippet", snippet);
        return m;
    }

    private String text(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
