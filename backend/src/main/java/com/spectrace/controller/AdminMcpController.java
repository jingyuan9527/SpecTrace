package com.spectrace.controller;

import com.spectrace.common.PageResult;
import com.spectrace.common.R;
import com.spectrace.dto.McpAgentCreateRequest;
import com.spectrace.dto.McpAgentCreatedVO;
import com.spectrace.dto.McpAgentUpdateRequest;
import com.spectrace.dto.McpAgentVO;
import com.spectrace.entity.McpCallLog;
import com.spectrace.mcp.service.McpAgentService;
import com.spectrace.mcp.service.McpCallLogService;
import com.spectrace.service.OperationLogService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 后台 MCP 接入管理。
 *
 * <p>读操作对 ADMIN 开放（排查谁在改数据），凭据创建与轮换只给 SUPER_ADMIN：
 * 一把 Key 等于一份数据写入权，签发权不能下放。</p>
 */
@Tag(name = "后台-MCP 接入管理")
@RestController
@RequestMapping("/api/admin/mcp")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('SUPER_ADMIN','ADMIN')")
public class AdminMcpController {

    private final McpAgentService agentService;

    private final McpCallLogService callLogService;

    private final OperationLogService logService;

    @Operation(summary = "MCP Agent 列表")
    @GetMapping("/agents")
    public R<List<McpAgentVO>> agents() {
        return R.ok(agentService.list());
    }

    @Operation(summary = "创建 MCP Agent（明文 Key 仅返回一次）")
    @PostMapping("/agents")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public R<McpAgentCreatedVO> create(@Valid @RequestBody McpAgentCreateRequest req,
                                       @RequestAttribute Long adminId,
                                       @RequestAttribute String adminName) {
        McpAgentCreatedVO created = agentService.create(req);
        logService.record(adminId, adminName, "CREATE_MCP_AGENT", "mcp_agent",
                created.getAgent().getId(), created.getAgent().getName());
        return R.ok(created);
    }

    @Operation(summary = "更新 MCP Agent 元数据与授权范围")
    @PutMapping("/agents/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public R<McpAgentVO> update(@PathVariable Long id,
                                @Valid @RequestBody McpAgentUpdateRequest req,
                                @RequestAttribute Long adminId,
                                @RequestAttribute String adminName) {
        McpAgentVO updated = agentService.update(id, req);
        logService.record(adminId, adminName, "UPDATE_MCP_AGENT", "mcp_agent", id, updated.getName());
        return R.ok(updated);
    }

    @Operation(summary = "轮换 MCP Agent 密钥（旧 Key 立即失效）")
    @PostMapping("/agents/{id}/rotate-key")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public R<McpAgentCreatedVO> rotateKey(@PathVariable Long id,
                                          @RequestAttribute Long adminId,
                                          @RequestAttribute String adminName) {
        McpAgentCreatedVO rotated = agentService.rotateKey(id);
        logService.record(adminId, adminName, "ROTATE_MCP_AGENT_KEY", "mcp_agent", id,
                rotated.getAgent().getName());
        return R.ok(rotated);
    }

    @Operation(summary = "删除 MCP Agent（其 Key 立即失效）")
    @DeleteMapping("/agents/{id}")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public R<Void> delete(@PathVariable Long id,
                          @RequestAttribute Long adminId,
                          @RequestAttribute String adminName) {
        agentService.delete(id);
        logService.record(adminId, adminName, "DELETE_MCP_AGENT", "mcp_agent", id, null);
        return R.ok();
    }

    @Operation(summary = "MCP 工具调用日志")
    @GetMapping("/logs")
    public R<PageResult<McpCallLog>> logs(@RequestParam(defaultValue = "1") long current,
                                          @RequestParam(defaultValue = "20") long size,
                                          @RequestParam(required = false) Long agentId,
                                          @RequestParam(required = false) String toolName,
                                          @RequestParam(required = false) String status) {
        return R.ok(PageResult.of(callLogService.page(current, size, agentId, toolName, status)));
    }
}
