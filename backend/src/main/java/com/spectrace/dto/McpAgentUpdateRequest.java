package com.spectrace.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 更新 MCP Agent 元数据；为 null 的字段保持不变。密钥轮换走独立接口，避免误改。
 */
@Data
public class McpAgentUpdateRequest {

    @Size(max = 64)
    private String name;

    @Size(max = 255)
    private String description;

    private String role;

    private List<String> scopes;

    private Integer status;
}
