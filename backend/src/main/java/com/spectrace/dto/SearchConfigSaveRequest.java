package com.spectrace.dto;

import lombok.Data;

/** 搜索源配置保存：Key 留空表示不修改。 */
@Data
public class SearchConfigSaveRequest {
    /** system / tavily / searxng */
    private String provider;
    private String tavilyApiKey;
    private String searxngBaseUrl;
    private String searxngToken;
    private String serperApiKey;
}
