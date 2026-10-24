package com.spectrace.service;

import com.spectrace.agent.config.AgentProperties;
import com.spectrace.common.BizException;
import com.spectrace.dto.SearchConfigSaveRequest;
import com.spectrace.dto.SearchConfigVO;
import com.spectrace.dto.SearchTestResultVO;
import com.spectrace.entity.AgentSearchConfig;
import com.spectrace.mapper.AgentSearchConfigMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 采集搜索源配置（单行表）：可同时启用多个引擎，运行时按 Tavily → SearXNG → Serper 顺序
 * 依次尝试第一个可用引擎，全部失败回落 DuckDuckGo；一个都没启用即系统默认链。
 * 采集工具每次搜索时重新读取，后台改完下个任务即生效，无需重启。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchConfigService {

    private static final Set<String> ENGINES = Set.of("tavily", "searxng", "serper");

    private final AgentSearchConfigMapper configMapper;
    private final AgentProperties props;
    private final WebSearchService webSearchService;

    public AgentSearchConfig getOrCreate() {
        AgentSearchConfig config = configMapper.selectById(AgentSearchConfig.SINGLETON_ID);
        if (config != null) {
            return config;
        }
        config = new AgentSearchConfig();
        config.setId(AgentSearchConfig.SINGLETON_ID);
        config.setProvider("");
        configMapper.insert(config);
        return config;
    }

    public SearchConfigVO getVO() {
        AgentSearchConfig config = getOrCreate();
        SearchConfigVO vo = new SearchConfigVO();
        vo.setEngines(parseEngines(config.getProvider()));
        vo.setTavilyApiKeySet(StringUtils.hasText(config.getTavilyApiKey()));
        vo.setSearxngBaseUrl(config.getSearxngBaseUrl());
        vo.setSearxngTokenSet(StringUtils.hasText(config.getSearxngToken()));
        vo.setSerperApiKeySet(StringUtils.hasText(config.getSerperApiKey()));
        vo.setUpdatedAt(config.getUpdatedAt());
        return vo;
    }

    public SearchConfigVO save(SearchConfigSaveRequest req) {
        List<String> engines = normalizeEngines(req == null ? null : req.getEngines());
        AgentSearchConfig config = getOrCreate();
        // Key 类字段留空表示不修改，避免回显时把密钥明文带出去再存回
        if (StringUtils.hasText(req.getTavilyApiKey())) {
            config.setTavilyApiKey(req.getTavilyApiKey().trim());
        }
        if (StringUtils.hasText(req.getSearxngToken())) {
            config.setSearxngToken(req.getSearxngToken().trim());
        }
        if (StringUtils.hasText(req.getSerperApiKey())) {
            config.setSerperApiKey(req.getSerperApiKey().trim());
        }
        config.setSearxngBaseUrl(StringUtils.hasText(req.getSearxngBaseUrl())
                ? req.getSearxngBaseUrl().trim().replaceAll("/+$", "")
                : null);
        validateEngines(engines, config);
        config.setProvider(String.join(",", engines));
        config.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(config);
        log.info("搜索源配置已保存: engines={} tavilyKey={} searxngUrl={} searxngToken={} serperKey={}",
                engines,
                StringUtils.hasText(config.getTavilyApiKey()) ? "已配置" : "无",
                config.getSearxngBaseUrl(),
                StringUtils.hasText(config.getSearxngToken()) ? "已配置" : "无",
                StringUtils.hasText(config.getSerperApiKey()) ? "已配置" : "无");
        return getVO();
    }

    /** 采集运行时视图：Serper Key 库里没配时回落到环境变量。 */
    public WebSearchService.SearchRuntime resolve() {
        AgentSearchConfig config = getOrCreate();
        WebSearchService.SearchRuntime runtime = new WebSearchService.SearchRuntime();
        runtime.setEngines(parseEngines(config.getProvider()));
        runtime.setTavilyApiKey(config.getTavilyApiKey());
        runtime.setSearxngBaseUrl(config.getSearxngBaseUrl());
        runtime.setSearxngToken(config.getSearxngToken());
        runtime.setSerperApiKey(StringUtils.hasText(config.getSerperApiKey())
                ? config.getSerperApiKey()
                : props.getSerperApiKey());
        return runtime;
    }

    /**
     * 真实搜一次验证可用性。req 非空时用「未保存的表单值」测试（Key 留空沿用已存值），
     * 为空时测试已保存配置；启用 Tavily 时顺带查询用量（/usage 有速率限制，只在测试时调用）。
     */
    public SearchTestResultVO test(SearchConfigSaveRequest req) {
        AgentSearchConfig saved = getOrCreate();
        WebSearchService.SearchRuntime runtime = new WebSearchService.SearchRuntime();
        List<String> engines = req != null && req.getEngines() != null
                ? normalizeEngines(req.getEngines())
                : parseEngines(saved.getProvider());
        runtime.setEngines(engines);
        runtime.setTavilyApiKey(pickKey(req == null ? null : req.getTavilyApiKey(), saved.getTavilyApiKey()));
        runtime.setSearxngBaseUrl(StringUtils.hasText(req == null ? null : req.getSearxngBaseUrl())
                ? req.getSearxngBaseUrl().trim().replaceAll("/+$", "")
                : saved.getSearxngBaseUrl());
        runtime.setSearxngToken(pickKey(req == null ? null : req.getSearxngToken(), saved.getSearxngToken()));
        runtime.setSerperApiKey(StringUtils.hasText(pickKey(req == null ? null : req.getSerperApiKey(), saved.getSerperApiKey()))
                ? pickKey(req == null ? null : req.getSerperApiKey(), saved.getSerperApiKey())
                : props.getSerperApiKey());
        validateRuntime(engines, runtime);

        SearchTestResultVO vo = new SearchTestResultVO();
        long start = System.currentTimeMillis();
        try {
            WebSearchService.Outcome outcome = webSearchService.search(runtime, "iPhone 17 参数 规格");
            vo.setSuccess(outcome.results() != null && !outcome.results().isEmpty());
            vo.setEngine(outcome.engine());
            vo.setCount(outcome.results() == null ? 0 : outcome.results().size());
            vo.setError(vo.isSuccess() ? null : "搜索引擎未返回任何结果");
        } catch (Exception e) {
            vo.setSuccess(false);
            vo.setEngine(String.join("→", engines));
            vo.setError(e.getClass().getSimpleName() + ": " + e.getMessage());
            log.warn("搜索源测试失败 engines={}: {}", engines, e.getMessage());
        }
        vo.setLatencyMs(System.currentTimeMillis() - start);
        if (engines.contains("tavily") && StringUtils.hasText(runtime.getTavilyApiKey())) {
            // 用量属附加信息，失败不影响测试结论
            vo.setTavilyUsage(webSearchService.fetchTavilyUsage(runtime.getTavilyApiKey()));
        }
        return vo;
    }

    /** 保存前校验：启用的引擎必须具备对应凭据。 */
    private void validateEngines(List<String> engines, AgentSearchConfig config) {
        if (engines.contains("tavily") && !StringUtils.hasText(config.getTavilyApiKey())) {
            throw new BizException("启用 Tavily 需先填写 API Key");
        }
        if (engines.contains("searxng") && !StringUtils.hasText(config.getSearxngBaseUrl())) {
            throw new BizException("启用 SearXNG 需先填写实例地址");
        }
        if (engines.contains("serper") && !StringUtils.hasText(config.getSerperApiKey())
                && !StringUtils.hasText(props.getSerperApiKey())) {
            throw new BizException("启用 Serper 需先填写 API Key（或配置环境变量 SERPER_API_KEY）");
        }
    }

    private void validateRuntime(List<String> engines, WebSearchService.SearchRuntime runtime) {
        if (engines.contains("tavily") && !StringUtils.hasText(runtime.getTavilyApiKey())) {
            throw new BizException("尚未配置 Tavily API Key");
        }
        if (engines.contains("searxng") && !StringUtils.hasText(runtime.getSearxngBaseUrl())) {
            throw new BizException("尚未配置 SearXNG 实例地址");
        }
        if (engines.contains("serper") && !StringUtils.hasText(runtime.getSerperApiKey())) {
            throw new BizException("尚未配置 Serper API Key");
        }
    }

    /** 逗号分隔的引擎串解析为有序列表；历史值 system 表示未启用任何第三方引擎。 */
    private List<String> parseEngines(String raw) {
        if (!StringUtils.hasText(raw) || "system".equalsIgnoreCase(raw.trim())) {
            return List.of();
        }
        return normalizeEngines(Arrays.asList(raw.split(",")));
    }

    /** 去重、转小写、过滤非法值，保序。 */
    private List<String> normalizeEngines(List<String> input) {
        Set<String> result = new LinkedHashSet<>();
        if (input != null) {
            for (String item : input) {
                if (item == null) {
                    continue;
                }
                String token = item.trim().toLowerCase();
                if (ENGINES.contains(token)) {
                    result.add(token);
                }
            }
        }
        return new ArrayList<>(result);
    }

    private String pickKey(String override, String saved) {
        return StringUtils.hasText(override) ? override.trim() : saved;
    }
}
