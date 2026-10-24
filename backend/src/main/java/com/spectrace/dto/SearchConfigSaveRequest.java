package com.spectrace.dto;

import lombok.Data;

import java.util.List;

/** 搜索源配置保存：engines 为启用的引擎（tavily/searxng/serper，可多个）；Key 留空表示不修改。 */
@Data
public class SearchConfigSaveRequest {
    private List<String> engines;
    private String tavilyApiKey;
    private String searxngBaseUrl;
    private String searxngToken;
    private String serperApiKey;
}
