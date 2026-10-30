package com.spectrace.mcp.tool;

import com.spectrace.common.NotFoundException;
import com.spectrace.entity.Feedback;
import com.spectrace.mapper.FeedbackMapper;
import com.spectrace.mcp.auth.McpScope;
import com.spectrace.mcp.config.McpProperties;
import com.spectrace.service.FeedbackService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 访客纠错反馈处理。
 *
 * <p>OPEN → RESOLVED / IGNORED 是终态，处理时写入 handlerNote；状态与备注都保留在库里，
 * 便于后续核查「为什么这条纠错没被采纳」。</p>
 */
@Component
@RequiredArgsConstructor
public class FeedbackTools implements McpToolProvider {

    private final FeedbackService feedbackService;

    private final FeedbackMapper feedbackMapper;

    private final McpProperties properties;

    @Override
    public List<McpTool> tools() {
        return List.of(
                new McpTool("spectrace_feedback_list", "查询纠错反馈",
                        "分页查询访客提交的参数纠错，可按状态 OPEN / RESOLVED / IGNORED 筛选。",
                        McpScope.FEEDBACK_READ,
                        McpSchemas.object(McpSchemas.props(
                                "current", McpSchemas.integer("页码，从 1 开始，默认 1"),
                                "size", McpSchemas.integer("每页条数，默认 10"),
                                "status", McpSchemas.enumString("状态筛选",
                                        "OPEN", "RESOLVED", "IGNORED")
                        )),
                        (context, args) -> {
                            McpPageQuery page = McpPageQuery.of(args, properties.getMaxPageSize());
                            var result = feedbackService.page(page.current(), page.size(),
                                    args.optString("status"));
                            return Map.of(
                                    "total", result.getTotal(),
                                    "current", result.getCurrent(),
                                    "size", result.getSize(),
                                    "records", result.getRecords());
                        }),

                new McpTool("spectrace_feedback_get", "查询纠错详情",
                        "按 id 查询单条纠错反馈。",
                        McpScope.FEEDBACK_READ,
                        McpSchemas.object(McpSchemas.props(
                                "id", McpSchemas.integer("反馈 id", 1, null)), "id"),
                        (context, args) -> {
                            Long id = args.requireLong("id");
                            Feedback feedback = feedbackMapper.selectById(id);
                            if (feedback == null) {
                                throw new NotFoundException("反馈不存在：" + id);
                            }
                            return feedback;
                        }),

                new McpTool("spectrace_feedback_update_status", "处理纠错反馈",
                        """
                                更新反馈状态并记录处理备注。status 只能是 RESOLVED 或 IGNORED；
                                note 会写入 handlerNote，建议写清处理依据。
                                """,
                        McpScope.FEEDBACK_WRITE,
                        McpSchemas.object(McpSchemas.props(
                                "id", McpSchemas.integer("反馈 id", 1, null),
                                "status", McpSchemas.enumString("处理结果", "RESOLVED", "IGNORED"),
                                "note", McpSchemas.string("处理备注")
                        ), "id", "status"),
                        (context, args) -> {
                            Long id = args.requireLong("id");
                            String status = args.requireString("status");
                            feedbackService.updateStatus(id, status, args.optString("note"),
                                    context.auditAdminId(), context.auditAdminName());
                            return Map.of("id", id, "status", status, "updated", true);
                        })
        );
    }
}
