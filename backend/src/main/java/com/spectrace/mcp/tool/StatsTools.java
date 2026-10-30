package com.spectrace.mcp.tool;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.spectrace.entity.AgentPendingData;
import com.spectrace.entity.AgentTask;
import com.spectrace.entity.Device;
import com.spectrace.entity.Feedback;
import com.spectrace.entity.McpCallLog;
import com.spectrace.entity.OperationLog;
import com.spectrace.mapper.AgentPendingDataMapper;
import com.spectrace.mapper.AgentTaskMapper;
import com.spectrace.mapper.BrandMapper;
import com.spectrace.mapper.DeviceMapper;
import com.spectrace.mapper.DeviceSeriesMapper;
import com.spectrace.mapper.FeedbackMapper;
import com.spectrace.mapper.McpCallLogMapper;
import com.spectrace.mapper.OperationLogMapper;
import com.spectrace.mapper.ParamDefMapper;
import com.spectrace.mapper.ParamGroupMapper;
import com.spectrace.mcp.auth.McpScope;
import com.spectrace.mcp.config.McpProperties;
import com.spectrace.mcp.service.McpCallLogService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 只读统计与审计查询。
 *
 * <p>给 Agent 一个「改完能自查」的入口：操作日志里能查到 mcp:&lt;agent&gt; 的写入记录，
 * 调用日志里能查到每次工具调用的入参与耗时。</p>
 */
@Component
@RequiredArgsConstructor
public class StatsTools implements McpToolProvider {

    private final BrandMapper brandMapper;

    private final DeviceMapper deviceMapper;

    private final DeviceSeriesMapper seriesMapper;

    private final ParamGroupMapper paramGroupMapper;

    private final ParamDefMapper paramDefMapper;

    private final AgentPendingDataMapper pendingMapper;

    private final AgentTaskMapper taskMapper;

    private final FeedbackMapper feedbackMapper;

    private final OperationLogMapper operationLogMapper;

    private final McpCallLogMapper callLogMapper;

    private final McpCallLogService callLogService;

    private final McpProperties properties;

    @Override
    public List<McpTool> tools() {
        return List.of(
                new McpTool("spectrace_stats_overview", "查询数据概览",
                        "返回品牌、系列、机型（上架/下架）、参数定义、待审核、未处理纠错、采集任务与 MCP 调用的数量统计。",
                        McpScope.STATS_READ,
                        McpSchemas.object(McpSchemas.props()),
                        (context, args) -> {
                            long total = count(deviceMapper.selectCount(null));
                            long online = count(deviceMapper.selectCount(
                                    Wrappers.<Device>lambdaQuery().eq(Device::getStatus, 1)));
                            long offline = total - online;

                            Map<String, Object> devices = new LinkedHashMap<>();
                            devices.put("total", total);
                            devices.put("online", online);
                            devices.put("offline", offline);

                            Map<String, Object> params = new LinkedHashMap<>();
                            params.put("groups", count(paramGroupMapper.selectCount(null)));
                            params.put("defs", count(paramDefMapper.selectCount(null)));

                            Map<String, Object> tasks = new LinkedHashMap<>();
                            tasks.put("queued", count(taskMapper.selectCount(
                                    Wrappers.<AgentTask>lambdaQuery().eq(AgentTask::getStatus, "QUEUED"))));
                            tasks.put("running", count(taskMapper.selectCount(
                                    Wrappers.<AgentTask>lambdaQuery().eq(AgentTask::getStatus, "RUNNING"))));
                            tasks.put("failed", count(taskMapper.selectCount(
                                    Wrappers.<AgentTask>lambdaQuery().eq(AgentTask::getStatus, "FAILED"))));

                            LocalDateTime since = LocalDateTime.now().minusHours(24);
                            Map<String, Object> mcpCalls = new LinkedHashMap<>();
                            mcpCalls.put("total", count(callLogMapper.selectCount(null)));
                            mcpCalls.put("last24h", count(callLogMapper.selectCount(
                                    Wrappers.<McpCallLog>lambdaQuery().ge(McpCallLog::getCreatedAt, since))));

                            Map<String, Object> data = new LinkedHashMap<>();
                            data.put("brands", count(brandMapper.selectCount(null)));
                            data.put("series", count(seriesMapper.selectCount(null)));
                            data.put("devices", devices);
                            data.put("params", params);
                            data.put("pendingReview", count(pendingMapper.selectCount(
                                    Wrappers.<AgentPendingData>lambdaQuery()
                                            .eq(AgentPendingData::getStatus, "PENDING_REVIEW"))));
                            data.put("feedbackOpen", count(feedbackMapper.selectCount(
                                    Wrappers.<Feedback>lambdaQuery().eq(Feedback::getStatus, "OPEN"))));
                            data.put("agentTasks", tasks);
                            data.put("mcpCalls", mcpCalls);
                            return data;
                        }),

                new McpTool("spectrace_operation_log_list", "查询数据操作日志",
                        """
                                分页查询 operation_log，可按动作、目标类型与操作来源筛选。
                                MCP 写入记为 adminName = mcp:<agent>，source=MCP 可只看 Agent 的改动。
                                """,
                        McpScope.STATS_READ,
                        McpSchemas.object(McpSchemas.props(
                                "current", McpSchemas.integer("页码，从 1 开始，默认 1"),
                                "size", McpSchemas.integer("每页条数，默认 20"),
                                "action", McpSchemas.string("动作，如 CREATE_DEVICE / UPDATE_DEVICE"),
                                "targetType", McpSchemas.string("目标类型，如 device / brand / series"),
                                "source", McpSchemas.enumString("操作来源", "ALL", "MCP", "MANUAL")
                        )),
                        (context, args) -> {
                            McpPageQuery page = McpPageQuery.of(args, properties.getMaxPageSize());
                            var query = Wrappers.<OperationLog>lambdaQuery()
                                    .orderByDesc(OperationLog::getCreatedAt)
                                    .orderByDesc(OperationLog::getId);
                            String action = args.optString("action");
                            if (action != null) {
                                query.eq(OperationLog::getAction, action);
                            }
                            String targetType = args.optString("targetType");
                            if (targetType != null) {
                                query.eq(OperationLog::getTargetType, targetType);
                            }
                            String source = args.optString("source");
                            if ("MCP".equalsIgnoreCase(source)) {
                                query.likeRight(OperationLog::getAdminName, "mcp:");
                            } else if ("MANUAL".equalsIgnoreCase(source)) {
                                query.notLikeRight(OperationLog::getAdminName, "mcp:");
                            }
                            var result = operationLogMapper.selectPage(
                                    new com.baomidou.mybatisplus.extension.plugins.pagination.Page<>(
                                            page.current(), page.size()), query);
                            return Map.of(
                                    "total", result.getTotal(),
                                    "current", result.getCurrent(),
                                    "size", result.getSize(),
                                    "records", result.getRecords());
                        }),

                new McpTool("spectrace_mcp_call_log_list", "查询 MCP 调用日志",
                        "分页查询所有 Agent 的工具调用记录，含入参（已脱敏）、状态、错误码、耗时与客户端信息。",
                        McpScope.STATS_READ,
                        McpSchemas.object(McpSchemas.props(
                                "current", McpSchemas.integer("页码，从 1 开始，默认 1"),
                                "size", McpSchemas.integer("每页条数，默认 20"),
                                "agentId", McpSchemas.integer("Agent id"),
                                "toolName", McpSchemas.string("工具名，如 spectrace_device_list"),
                                "status", McpSchemas.enumString("调用结果", "SUCCESS", "ERROR", "DENIED")
                        )),
                        (context, args) -> {
                            McpPageQuery page = McpPageQuery.of(args, properties.getMaxPageSize());
                            var result = callLogService.page(page.current(), page.size(),
                                    args.optLong("agentId"), args.optString("toolName"),
                                    args.optString("status"));
                            return Map.of(
                                    "total", result.getTotal(),
                                    "current", result.getCurrent(),
                                    "size", result.getSize(),
                                    "records", result.getRecords());
                        })
        );
    }

    private long count(Long value) {
        return value == null ? 0L : value;
    }
}
