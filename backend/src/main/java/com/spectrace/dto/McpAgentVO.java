package com.spectrace.dto;

import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Agent 对外视图：不含密钥哈希，只暴露前缀。
 */
@Data
public class McpAgentVO {

    private Long id;

    private String name;

    private String description;

    private String apiKeyPrefix;

    private String role;

    private List<String> scopes;

    private Integer status;

    private Long callCount;

    private LocalDateTime lastUsedAt;

    private LocalDateTime createdAt;
}
