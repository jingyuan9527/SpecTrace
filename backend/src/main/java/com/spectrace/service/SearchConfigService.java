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
import java.util.Set;

/**
 * 采集搜索源配置（单行表）：配了 Tavily / SearXNG 就用所选引擎，没配走默认（Serper Key 或 DuckDuckGo）。
 * 采集工具每次搜索时重新读取，后台改完下个任务即生效，无需重启。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SearchConfigService {

    private static final Set<String> PROVIDERS = Set.of("system", "tavily", "searxng");

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
        config.setProvider("system");
        configMapper.insert(config);
        return config;
    }

    public SearchConfigVO getVO() {
        AgentSearchConfig config = getOrCreate();
        SearchConfigVO vo = new SearchConfigVO();
        vo.setProvider(config.getProvider());
        vo.setTavilyApiKeySet(StringUtils.hasText(config.getTavilyApiKey()));
        vo.setSearxngBaseUrl(config.getSearxngBaseUrl());
        vo.setSearxngTokenSet(StringUtils.hasText(config.getSearxngToken()));
        vo.setSerperApiKeySet(StringUtils.hasText(config.getSerperApiKey()));
        vo.setUpdatedAt(config.getUpdatedAt());
        return vo;
    }

    public SearchConfigVO save(SearchConfigSaveRequest req) {
        String provider = req.getProvider() == null ? "system" : req.getProvider().trim().toLowerCase();
        if (!PROVIDERS.contains(provider)) {
            throw new BizException("搜索引擎只能是 system / tavily / searxng");
        }
        if ("tavily".equals(provider) && !StringUtils.hasText(req.getTavilyApiKey())
                && !StringUtils.hasText(getOrCreate().getTavilyApiKey())) {
            throw new BizException("选择 Tavily 需先填写 API Key");
        }
        if ("searxng".equals(provider)) {
            String existing = getOrCreate().getSearxngBaseUrl();
            boolean hasNew = StringUtils.hasText(req.getSearxngBaseUrl());
            if (!hasNew && !StringUtils.hasText(existing)) {
                throw new BizException("选择 SearXNG 需先填写实例地址");
            }
        }
        AgentSearchConfig config = getOrCreate();
        config.setProvider(provider);
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
        config.setUpdatedAt(LocalDateTime.now());
        configMapper.updateById(config);
        log.info("搜索源配置已保存: provider={} tavilyKey={} searxngUrl={} searxngToken={} serperKey={}",
                provider,
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
        runtime.setProvider(config.getProvider() == null ? "system" : config.getProvider());
        runtime.setTavilyApiKey(config.getTavilyApiKey());
        runtime.setSearxngBaseUrl(config.getSearxngBaseUrl());
        runtime.setSearxngToken(config.getSearxngToken());
        runtime.setSerperApiKey(StringUtils.hasText(config.getSerperApiKey())
                ? config.getSerperApiKey()
                : props.getSerperApiKey());
        return runtime;
    }

    /** 用已保存的配置真实搜一次，验证地址与 Key 可用性。 */
    public SearchTestResultVO test() {
        WebSearchService.SearchRuntime runtime = resolve();
        if ("tavily".equals(runtime.getProvider()) && !StringUtils.hasText(runtime.getTavilyApiKey())) {
            throw new BizException("尚未配置 Tavily API Key");
        }
        if ("searxng".equals(runtime.getProvider()) && !StringUtils.hasText(runtime.getSearxngBaseUrl())) {
            throw new BizException("尚未配置 SearXNG 实例地址");
        }
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
            vo.setEngine(runtime.getProvider());
            vo.setError(e.getClass().getSimpleName() + ": " + e.getMessage());
            log.warn("搜索源测试失败 provider={}: {}", runtime.getProvider(), e.getMessage());
        }
        vo.setLatencyMs(System.currentTimeMillis() - start);
        return vo;
    }
}
