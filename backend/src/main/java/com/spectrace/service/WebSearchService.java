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
        /** system / tavily / searxng */
        private String provider;
        private String tavilyApiKey;
        private String searxngBaseUrl;
        private String searxngToken;
        private String serperApiKey;
    }

    /** 一次搜索的结果：实际使用的引擎 + title/url/snippet 列表。 */
    public record Outcome(String engine, List<Map<String, Object>> results) {
    }

    /**
     * 按配置执行搜索。所选引擎抛错时回落 DuckDuckGo；DuckDuckGo 也失败时返回空结果而非异常，
     * 调用方（采集工具）只负责记录，不需要为搜索失败做容错。
     */
    public Outcome search(SearchRuntime cfg, String query) {
        String provider = cfg.getProvider() == null ? "system" : cfg.getProvider();
        try {
            return switch (provider) {
                case "tavily" -> new Outcome("tavily", searchTavily(cfg, query));
                case "searxng" -> new Outcome("searxng", searchSearxng(cfg, query));
                default -> searchDefault(cfg, query);
            };
        } catch (Exception e) {
            log.warn("搜索引擎 {} 失败，回落 DuckDuckGo: {}", provider, e.getMessage());
            try {
                return new Outcome("duckduckgo", searchDuckDuckGo(query));
            } catch (Exception fallback) {
                log.warn("DuckDuckGo 搜索也失败: {}", fallback.getMessage());
                return new Outcome("none", List.of());
            }
        }
    }

    /** 默认链：有 Serper Key（后台或环境变量）走 Google 结果，否则 DuckDuckGo。 */
    private Outcome searchDefault(SearchRuntime cfg, String query) throws Exception {
        if (cfg.getSerperApiKey() != null && !cfg.getSerperApiKey().isBlank()) {
            return new Outcome("serper", searchSerper(cfg.getSerperApiKey(), query));
        }
        return new Outcome("duckduckgo", searchDuckDuckGo(query));
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
