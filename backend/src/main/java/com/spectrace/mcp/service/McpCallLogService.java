package com.spectrace.mcp.service;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.entity.McpCallLog;
import com.spectrace.mapper.McpCallLogMapper;
import com.spectrace.mcp.config.McpProperties;
import com.spectrace.mcp.tool.McpToolContext;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

/**
 * MCP 调用审计落库。
 *
 * <p>审计失败绝不能改变工具调用的成败：日志写不进去只丢可观测性，让业务调用回滚则会造成
 * 「Agent 以为没改成功、实际数据已变」的更严重不一致。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpCallLogService {

    /** 参数里出现这些键一律脱敏，防止 Agent 把凭据顺手塞进业务字段 */
    private static final Set<String> SENSITIVE_KEYS = Set.of(
            "apikey", "api_key", "key", "token", "password", "secret", "authorization");

    private final McpCallLogMapper callLogMapper;

    private final McpProperties properties;

    private final ObjectMapper objectMapper;

    public void record(McpToolContext context, String toolName, Map<String, Object> arguments,
                       String status, String errorCode, String errorMessage, long durationMs) {
        try {
            McpCallLog entry = new McpCallLog();
            if (context != null) {
                entry.setAgentId(context.principal().agentId());
                entry.setAgentName(context.principal().agentName());
                entry.setClientName(context.clientName());
                entry.setSessionId(context.sessionId());
            }
            entry.setToolName(toolName);
            entry.setArgumentsJson(truncate(serialize(mask(arguments))));
            entry.setStatus(status);
            entry.setErrorCode(errorCode);
            entry.setErrorMessage(truncate(errorMessage));
            entry.setDurationMs(durationMs);
            callLogMapper.insert(entry);
        } catch (Exception e) {
            log.error("MCP 调用日志写入失败: tool={} status={}", toolName, status, e);
        }
    }

    public IPage<McpCallLog> page(long current, long size, Long agentId, String toolName, String status) {
        var query = Wrappers.<McpCallLog>lambdaQuery().orderByDesc(McpCallLog::getId);
        if (agentId != null) {
            query.eq(McpCallLog::getAgentId, agentId);
        }
        if (StringUtils.hasText(toolName)) {
            query.eq(McpCallLog::getToolName, toolName.trim());
        }
        if (StringUtils.hasText(status)) {
            query.eq(McpCallLog::getStatus, status.trim().toUpperCase());
        }
        return callLogMapper.selectPage(new Page<>(current, size), query);
    }

    private Map<String, Object> mask(Map<String, Object> arguments) {
        if (arguments == null) {
            return Map.of();
        }
        Map<String, Object> masked = new LinkedHashMap<>();
        arguments.forEach((key, value) -> {
            if (key != null && SENSITIVE_KEYS.contains(key.toLowerCase())) {
                masked.put(key, "***");
            } else if (value instanceof Map<?, ?> nested) {
                Map<String, Object> nestedMasked = new LinkedHashMap<>();
                nested.forEach((nestedKey, nestedValue) ->
                        nestedMasked.put(String.valueOf(nestedKey), nestedValue));
                masked.put(key, mask(nestedMasked));
            } else {
                masked.put(key, value);
            }
        });
        return masked;
    }

    private String serialize(Map<String, Object> arguments) {
        try {
            return objectMapper.writeValueAsString(arguments);
        } catch (Exception e) {
            log.warn("MCP 调用参数序列化失败，降级为字符串", e);
            return String.valueOf(arguments);
        }
    }

    private String truncate(String text) {
        if (text == null) {
            return null;
        }
        int max = Math.max(64, properties.getLogArgumentsMaxLength());
        return text.length() <= max ? text : text.substring(0, max) + "...(truncated)";
    }
}
