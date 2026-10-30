package com.spectrace.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.util.List;

/**
 * 创建 MCP Agent 的请求。scopes 留空时按 role 给默认值。
 */
@Data
public class McpAgentCreateRequest {

    @NotBlank
    @Size(max = 64)
    private String name;

    @Size(max = 255)
    private String description;

    /** READ_ONLY / EDITOR / ADMIN */
    private String role;

    /** 显式授权范围，如 ["device:read","device:write"]；留空按 role 默认 */
    private List<String> scopes;

    /** 1 启用 / 0 停用，默认启用 */
    private Integer status;
}
