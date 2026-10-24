package com.spectrace.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/** 搜索源配置回显：Key 只回是否已配置，不回内容。 */
@Data
public class SearchConfigVO {
    /** 启用的引擎列表（tavily/searxng/serper，按尝试顺序），空表示系统默认链 */
    private List<String> engines;
    private Boolean tavilyApiKeySet;
    private String searxngBaseUrl;
    private Boolean searxngTokenSet;
    private Boolean serperApiKeySet;
    private LocalDateTime updatedAt;
}
