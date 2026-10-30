package com.spectrace.mcp.tool;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.common.BizException;
import com.spectrace.common.NotFoundException;
import com.spectrace.common.WriteBusyException;
import com.spectrace.db.SqliteErrors;
import com.spectrace.mcp.auth.McpAgentPrincipal;
import com.spectrace.mcp.auth.McpApiKeyFilter;
import com.spectrace.mcp.auth.McpScope;
import com.spectrace.mcp.service.McpAgentService;
import com.spectrace.mcp.service.McpCallLogService;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 工具注册与统一执行壳。
 *
 * <p>鉴权、异常映射、审计、结果包装只在这里做一次：工具实现只关心业务，
 * Agent 拿到的错误码也保持一致，接入方不需要按工具逐个适配错误格式。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpToolRegistry {

    private final List<McpToolProvider> providers;

    private final McpCallLogService callLogService;

    private final McpAgentService agentService;

    private final ObjectMapper objectMapper;

    private volatile List<McpServerFeatures.SyncToolSpecification> specifications;

    public List<McpServerFeatures.SyncToolSpecification> specifications() {
        if (specifications == null) {
            synchronized (this) {
                if (specifications == null) {
                    specifications = buildSpecifications();
                }
            }
        }
        return specifications;
    }

    public List<String> toolNames() {
        return specifications().stream().map(spec -> spec.tool().name()).toList();
    }

    private List<McpServerFeatures.SyncToolSpecification> buildSpecifications() {
        List<McpTool> tools = new ArrayList<>();
        for (McpToolProvider provider : providers) {
            tools.addAll(provider.tools());
        }
        Set<String> names = new HashSet<>();
        List<McpServerFeatures.SyncToolSpecification> list = new ArrayList<>(tools.size());
        for (McpTool tool : tools) {
            if (!names.add(tool.name())) {
                throw new IllegalStateException("MCP 工具名重复：" + tool.name());
            }
            McpSchema.Tool schema = McpSchema.Tool.builder()
                    .name(tool.name())
                    .title(tool.title())
                    .description(tool.description())
                    .inputSchema(tool.inputSchema())
                    .build();
            list.add(McpServerFeatures.SyncToolSpecification.builder()
                    .tool(schema)
                    .callHandler((exchange, request) -> invoke(tool, exchange, request.arguments()))
                    .build());
        }
        log.info("MCP 工具注册完成: {} 个，scope 覆盖 {}", list.size(),
                tools.stream().map(McpTool::requiredScope).filter(java.util.Objects::nonNull)
                        .map(McpScope::value).distinct().toList());
        return List.copyOf(list);
    }

    private McpSchema.CallToolResult invoke(McpTool tool, McpSyncServerExchange exchange,
                                            Map<String, Object> arguments) {
        long startNanos = System.nanoTime();
        McpToolContext context = contextOf(exchange);
        String status = "SUCCESS";
        String errorCode = null;
        String errorMessage = null;
        Object data = null;
        boolean failed = false;

        try {
            if (context == null) {
                throw new McpAccessDeniedException(tool.requiredScope(), "unknown");
            }
            if (tool.requiredScope() != null && !context.principal().allows(tool.requiredScope())) {
                throw new McpAccessDeniedException(tool.requiredScope(), context.principal().agentName());
            }
            data = tool.handler().handle(context, new McpArgs(arguments));
        } catch (McpArgumentException e) {
            status = "ERROR";
            failed = true;
            errorCode = "INVALID_ARGUMENT";
            errorMessage = e.getField() + ": " + e.getMessage();
        } catch (McpAccessDeniedException e) {
            status = "DENIED";
            failed = true;
            errorCode = "FORBIDDEN";
            errorMessage = e.getMessage();
        } catch (NotFoundException e) {
            status = "ERROR";
            failed = true;
            errorCode = "NOT_FOUND";
            errorMessage = e.getMessage();
        } catch (WriteBusyException e) {
            // 写锁排队超时不是服务故障：调用方退避几十毫秒重试即可成功
            status = "ERROR";
            failed = true;
            errorCode = "RETRY_LATER";
            errorMessage = e.getMessage();
            log.warn("MCP 写操作排队未通过: tool={} agent={}", tool.name(),
                    context == null ? "unknown" : context.principal().agentName(), e);
        } catch (BizException e) {
            status = "ERROR";
            failed = true;
            errorCode = "BUSINESS_ERROR";
            errorMessage = e.getMessage();
        } catch (DataIntegrityViolationException e) {
            status = "ERROR";
            failed = true;
            errorCode = "CONFLICT";
            errorMessage = "数据冲突：唯一约束或外键校验未通过，请先检查是否已存在同名/同键记录";
            log.warn("MCP 工具触发数据约束冲突: tool={} agent={}",
                    tool.name(), context == null ? "unknown" : context.principal().agentName(), e);
        } catch (Exception e) {
            status = "ERROR";
            failed = true;
            if (SqliteErrors.isBusy(e) || SqliteErrors.isWriteBusy(e)) {
                // 写锁冲突属于可重试状态：包装成 RETRY_LATER，别让调用方以为服务坏了
                errorCode = "RETRY_LATER";
                errorMessage = "数据库写锁繁忙，请稍后重试";
                log.warn("MCP 工具遇到 SQLite 写锁冲突: tool={} agent={} cause={}", tool.name(),
                        context == null ? "unknown" : context.principal().agentName(),
                        SqliteErrors.describe(e));
            } else {
                errorCode = "INTERNAL_ERROR";
                errorMessage = "服务内部错误，请稍后重试";
                log.error("MCP 工具执行失败: tool={} agent={} args={}",
                        tool.name(), context == null ? "unknown" : context.principal().agentName(),
                        safeArguments(arguments), e);
            }
        }

        long durationMs = (System.nanoTime() - startNanos) / 1_000_000;
        if (errorCode != null && expectedFailure(errorCode)) {
            log.info("MCP 工具返回失败: tool={} agent={} code={} message={}", tool.name(),
                    context == null ? "unknown" : context.principal().agentName(), errorCode, errorMessage);
        }
        callLogService.record(context, tool.name(), arguments, status, errorCode, errorMessage, durationMs);
        if (context != null) {
            agentService.recordUsage(context.principal().agentId());
        }

        return result(failed, errorCode == null ? "OK" : errorCode,
                failed ? errorMessage : "success", data, tool.name(), context, durationMs);
    }

    /** 业务失败与参数错误属于可预期结果，打 info 即可；日志级别区分开才能让 ERROR 保持可读 */
    private boolean expectedFailure(String errorCode) {
        return "INVALID_ARGUMENT".equals(errorCode) || "BUSINESS_ERROR".equals(errorCode)
                || "NOT_FOUND".equals(errorCode) || "FORBIDDEN".equals(errorCode)
                || "CONFLICT".equals(errorCode) || "RETRY_LATER".equals(errorCode);
    }

    private McpToolContext contextOf(McpSyncServerExchange exchange) {
        McpTransportContext transportContext = exchange.transportContext();
        Object value = transportContext == null
                ? null
                : transportContext.get(McpApiKeyFilter.PRINCIPAL_ATTRIBUTE);
        if (!(value instanceof McpAgentPrincipal principal)) {
            return null;
        }
        String clientName = null;
        if (exchange.getClientInfo() != null) {
            String name = exchange.getClientInfo().name();
            String version = exchange.getClientInfo().version();
            clientName = name + (version == null ? "" : "/" + version);
        }
        return new McpToolContext(principal, exchange.sessionId(), clientName);
    }

    private McpSchema.CallToolResult result(boolean failed, String code, String message, Object data,
                                            String toolName, McpToolContext context, long durationMs) {
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("success", !failed);
        payload.put("code", code);
        payload.put("message", message);
        if (data != null) {
            payload.put("data", jsonify(data));
        }

        Map<String, Object> meta = new LinkedHashMap<>();
        meta.put("tool", toolName);
        meta.put("durationMs", durationMs);
        if (context != null) {
            meta.put("agent", context.principal().agentName());
            meta.put("scopes", context.principal().scopes().stream().map(scope -> scope.value()).toList());
        }
        payload.put("meta", meta);

        McpSchema.CallToolResult.Builder builder = McpSchema.CallToolResult.builder()
                .isError(failed)
                .structuredContent(payload);
        try {
            builder.addTextContent(objectMapper.writeValueAsString(payload));
        } catch (Exception e) {
            // 结构化内容已经带上结果，文本通道序列化失败不必让整个调用失败
            log.warn("MCP 结果文本序列化失败: tool={}", toolName, e);
            builder.addTextContent(message);
        }
        return builder.build();
    }

    /** 统一经 Spring 的 ObjectMapper 转换，保证 LocalDate/LocalDateTime 的格式与 REST 接口一致 */
    private Object jsonify(Object data) {
        try {
            return objectMapper.convertValue(data, Object.class);
        } catch (Exception e) {
            log.warn("MCP 结果结构化失败，降级为字符串: type={}", data.getClass().getName(), e);
            return String.valueOf(data);
        }
    }

    private Object safeArguments(Map<String, Object> arguments) {
        if (arguments == null) {
            return Map.of();
        }
        try {
            return objectMapper.writeValueAsString(arguments);
        } catch (Exception e) {
            return "<unserializable>";
        }
    }
}
