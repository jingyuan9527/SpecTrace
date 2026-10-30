package com.spectrace.mcp.tool;

import com.spectrace.common.NotFoundException;
import com.spectrace.entity.DeviceSeries;
import com.spectrace.mcp.auth.McpScope;
import com.spectrace.service.DeviceSeriesService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 产品线（系列）维护。
 *
 * <p>所属品牌是系列的身份，不允许改；这一点必须在工具说明里写死，否则 Agent 会反复尝试
 * 「把系列挪到另一个品牌」并被服务端拒绝。</p>
 */
@Component
@RequiredArgsConstructor
public class SeriesTools implements McpToolProvider {

    private final DeviceSeriesService seriesService;

    @Override
    public List<McpTool> tools() {
        return List.of(
                new McpTool("spectrace_series_list", "查询系列列表",
                        "返回系列列表，可按品牌与启用状态筛选，关键词按系列名模糊匹配。",
                        McpScope.SERIES_READ,
                        McpSchemas.object(McpSchemas.props(
                                "brandId", McpSchemas.integer("所属品牌 id"),
                                "status", McpSchemas.integerEnum("1 启用 / 0 停用", 0, 1),
                                "keyword", McpSchemas.string("按系列名称模糊匹配")
                        )),
                        (context, args) -> {
                            Long brandId = args.optLong("brandId");
                            Integer status = args.optInt("status", null);
                            String keyword = args.optString("keyword");
                            List<DeviceSeries> series = seriesService.list(brandId, status);
                            if (keyword != null) {
                                series = series.stream()
                                        .filter(item -> item.getName() != null && item.getName().contains(keyword))
                                        .toList();
                            }
                            return Map.of("total", series.size(), "records", series);
                        }),

                new McpTool("spectrace_series_get", "查询系列详情",
                        "按 id 查询单个系列。",
                        McpScope.SERIES_READ,
                        McpSchemas.object(McpSchemas.props("id", McpSchemas.integer("系列 id", 1, null)), "id"),
                        (context, args) -> requireSeries(args.requireLong("id"))),

                new McpTool("spectrace_series_create", "创建系列",
                        "在指定品牌下创建系列。同一品牌内系列名唯一。",
                        McpScope.SERIES_WRITE,
                        McpSchemas.object(McpSchemas.props(
                                "brandId", McpSchemas.integer("所属品牌 id，必填", 1, null),
                                "name", McpSchemas.string("系列名称，同品牌内唯一"),
                                "sortOrder", McpSchemas.integer("展示顺序，越小越靠前"),
                                "status", McpSchemas.integerEnum("1 启用 / 0 停用，默认 1", 0, 1)
                        ), "brandId", "name"),
                        (context, args) -> {
                            DeviceSeries series = new DeviceSeries();
                            series.setBrandId(args.requireLong("brandId"));
                            series.setName(args.requireString("name"));
                            series.setSortOrder(args.optInt("sortOrder", 0));
                            series.setStatus(status(args.optInt("status", 1)));
                            return seriesService.save(series);
                        }),

                new McpTool("spectrace_series_update", "更新系列",
                        "按 id 更新系列名称、排序或状态。所属品牌不可修改，需要换品牌请新建系列后重新归类机型。",
                        McpScope.SERIES_WRITE,
                        McpSchemas.object(McpSchemas.props(
                                "id", McpSchemas.integer("系列 id", 1, null),
                                "name", McpSchemas.string("新名称"),
                                "sortOrder", McpSchemas.integer("展示顺序"),
                                "status", McpSchemas.integerEnum("1 启用 / 0 停用", 0, 1)
                        ), "id"),
                        (context, args) -> {
                            DeviceSeries existing = requireSeries(args.requireLong("id"));
                            String name = args.optString("name");
                            DeviceSeries series = new DeviceSeries();
                            series.setId(existing.getId());
                            series.setBrandId(existing.getBrandId());
                            series.setName(name == null ? existing.getName() : name);
                            series.setSortOrder(args.optInt("sortOrder", existing.getSortOrder()));
                            Integer status = args.optInt("status", null);
                            series.setStatus(status == null ? existing.getStatus() : status(status));
                            return seriesService.save(series);
                        }),

                new McpTool("spectrace_series_delete", "删除系列",
                        "逻辑删除系列。系列下仍有在册机型时会被拒绝，需先把机型改为未归类或迁到其它系列。",
                        McpScope.SERIES_WRITE,
                        McpSchemas.object(McpSchemas.props("id", McpSchemas.integer("系列 id", 1, null)), "id"),
                        (context, args) -> {
                            Long id = args.requireLong("id");
                            requireSeries(id);
                            seriesService.delete(id);
                            return Map.of("id", id, "deleted", true);
                        })
        );
    }

    private DeviceSeries requireSeries(Long id) {
        DeviceSeries series = seriesService.get(id);
        if (series == null) {
            throw new NotFoundException("系列不存在：" + id);
        }
        return series;
    }

    private Integer status(Integer value) {
        if (value != 0 && value != 1) {
            throw new McpArgumentException("status", "status 只能为 0（停用）或 1（启用）");
        }
        return value;
    }
}
