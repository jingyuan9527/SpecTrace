package com.spectrace.mcp.tool;

import com.spectrace.dto.ApproveRequest;
import com.spectrace.entity.AgentPendingData;
import com.spectrace.mcp.auth.McpScope;
import com.spectrace.mcp.config.McpProperties;
import com.spectrace.service.AgentReviewService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 抓取结果审核。
 *
 * <p>审核通过会把待审核数据合并进正式机型库，是唯一「Agent 采集数据」与「线上数据」之间的闸门；
 * 这里保留逐条与批量两条路径，批量接口返回逐条失败原因，不让一条脏数据卡住整批。</p>
 */
@Component
@RequiredArgsConstructor
public class ReviewTools implements McpToolProvider {

    private final AgentReviewService reviewService;

    private final McpProperties properties;

    @Override
    public List<McpTool> tools() {
        return List.of(
                new McpTool("spectrace_pending_list", "查询待审核数据",
                        """
                                分页查询 AI 抓取结果。默认只看 PENDING_REVIEW；status 可传
                                PENDING_REVIEW / APPROVED / DISCARDED / ALL。keyword 按机型名或品牌名模糊匹配。
                                """,
                        McpScope.REVIEW_READ,
                        McpSchemas.object(McpSchemas.props(
                                "current", McpSchemas.integer("页码，从 1 开始，默认 1"),
                                "size", McpSchemas.integer("每页条数，默认 10"),
                                "status", McpSchemas.enumString("状态筛选，ALL 表示不过滤",
                                        "PENDING_REVIEW", "APPROVED", "DISCARDED", "ALL"),
                                "keyword", McpSchemas.string("机型名 / 品牌名关键字")
                        )),
                        (context, args) -> {
                            McpPageQuery page = McpPageQuery.of(args, properties.getMaxPageSize());
                            String status = args.optString("status");
                            if (status == null) {
                                status = "PENDING_REVIEW";
                            }
                            if ("ALL".equalsIgnoreCase(status)) {
                                status = null;
                            }
                            var result = reviewService.pagePending(page.current(), page.size(), status,
                                    args.optString("keyword"));
                            List<?> records = result.getRecords().stream()
                                    .map(reviewService::toVO).toList();
                            return Map.of(
                                    "total", result.getTotal(),
                                    "current", result.getCurrent(),
                                    "size", result.getSize(),
                                    "records", records);
                        }),

                new McpTool("spectrace_pending_get", "查询待审核详情",
                        "返回待审核数据、解析后的参数、置信度、已入库同品牌同名机型的现有值（供 diff）与版本数据。",
                        McpScope.REVIEW_READ,
                        McpSchemas.object(McpSchemas.props(
                                "id", McpSchemas.integer("待审核数据 id", 1, null)), "id"),
                        (context, args) -> {
                            AgentPendingData pending = reviewService.getPending(args.requireLong("id"));
                            return reviewService.toDetailVO(pending);
                        }),

                new McpTool("spectrace_pending_approve", "审核通过待审核数据",
                        """
                                把待审核数据合并进正式机型库并返回新机型 id。
                                brandId 必填；name / releaseDate / thumbnail / description / params / variants
                                不传时回落到抓取结果。库中已有同品牌同名机型时按覆盖更新处理。
                                """,
                        McpScope.REVIEW_WRITE,
                        McpSchemas.object(McpSchemas.props(
                                "id", McpSchemas.integer("待审核数据 id", 1, null),
                                "brandId", McpSchemas.integer("品牌 id，必填", 1, null),
                                "name", McpSchemas.string("机型名称，默认取抓取结果"),
                                "releaseDate", McpSchemas.string("发布日期 yyyy-MM-dd"),
                                "thumbnail", McpSchemas.string("机型图地址"),
                                "description", McpSchemas.string("机型简介"),
                                "params", McpSchemas.stringMap("paramKey -> 参数值，默认取抓取结果"),
                                "variants", McpSchemas.variantArray("存储版本，默认取抓取结果")
                        ), "id", "brandId"),
                        (context, args) -> {
                            Long id = args.requireLong("id");
                            ApproveRequest req = new ApproveRequest();
                            req.setBrandId(args.requireLong("brandId"));
                            req.setName(args.optString("name"));
                            req.setReleaseDate(args.optDate("releaseDate"));
                            req.setThumbnail(args.optString("thumbnail"));
                            req.setDescription(args.optString("description"));
                            req.setParams(args.optStringMap("params"));
                            req.setVariants(McpPayloads.toVariants(args.optObjectList("variants")));
                            Long deviceId = reviewService.approve(id, req,
                                    context.auditAdminId(), context.auditAdminName());
                            return Map.of("pendingId", id, "deviceId", deviceId, "approved", true);
                        }),

                new McpTool("spectrace_pending_discard", "丢弃待审核数据",
                        "把待审核数据置为 DISCARDED，不写入机型库。",
                        McpScope.REVIEW_WRITE,
                        McpSchemas.object(McpSchemas.props(
                                "id", McpSchemas.integer("待审核数据 id", 1, null)), "id"),
                        (context, args) -> {
                            Long id = args.requireLong("id");
                            reviewService.discard(id, context.auditAdminId(), context.auditAdminName());
                            return Map.of("pendingId", id, "discarded", true);
                        }),

                new McpTool("spectrace_pending_batch_approve", "批量通过待审核数据",
                        "按抓取值批量入库，逐条独立事务提交，返回成功数与失败明细。",
                        McpScope.REVIEW_WRITE,
                        McpSchemas.object(McpSchemas.props(
                                "ids", McpSchemas.longArray("待审核数据 id 列表")
                        ), "ids"),
                        (context, args) -> {
                            List<Long> ids = args.requireLongList("ids");
                            // 批量审核按抓取结果里的品牌名解析品牌，不接受逐条覆盖
                            return reviewService.batchApprove(ids,
                                    context.auditAdminId(), context.auditAdminName());
                        }),

                new McpTool("spectrace_pending_batch_discard", "批量丢弃待审核数据",
                        "批量置为 DISCARDED，返回成功数与失败明细。",
                        McpScope.REVIEW_WRITE,
                        McpSchemas.object(McpSchemas.props(
                                "ids", McpSchemas.longArray("待审核数据 id 列表")), "ids"),
                        (context, args) -> reviewService.batchDiscard(args.requireLongList("ids"),
                                context.auditAdminId(), context.auditAdminName()))
        );
    }
}
