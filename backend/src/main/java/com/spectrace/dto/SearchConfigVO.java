package com.spectrace.dto;

import lombok.Data;

import java.time.LocalDateTime;

/** 搜索源配置回显：Key 只回是否已配置，不回内容。 */
@Data
public class SearchConfigVO {
    private String provider;
    private Boolean tavilyApiKeySet;
    private String searxngBaseUrl;
    private Boolean searxngTokenSet;
    private Boolean serperApiKeySet;
    private LocalDateTime updatedAt;
}
