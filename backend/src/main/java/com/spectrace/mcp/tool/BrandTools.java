package com.spectrace.mcp.tool;

import com.spectrace.common.NotFoundException;
import com.spectrace.entity.Brand;
import com.spectrace.mcp.auth.McpScope;
import com.spectrace.service.BrandService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

/**
 * 品牌维护。
 *
 * <p>品牌是机型与系列的父级，删除前必须确认没有机型引用：品牌被删后机型仍在，前台会显示成
 * 无主机型，排查成本远高于在删除时拦住。</p>
 */
@Component
@RequiredArgsConstructor
public class BrandTools implements McpToolProvider {

    private final BrandService brandService;

    @Override
    public List<McpTool> tools() {
        return List.of(
                new McpTool("spectrace_brand_list", "查询品牌列表",
                        "返回全部品牌，可按启用状态与名称关键字筛选。品牌数量有限，一次返回不分页。",
                        McpScope.BRAND_READ,
                        McpSchemas.object(McpSchemas.props(
                                "status", McpSchemas.integerEnum("1 启用 / 0 停用；不传返回全部", 0, 1),
                                "keyword", McpSchemas.string("按品牌名称模糊匹配")
                        )),
                        (context, args) -> {
                            Integer status = args.optInt("status", null);
                            String keyword = args.optString("keyword");
                            List<Brand> brands = brandService.list(status);
                            if (keyword != null) {
                                brands = brands.stream()
                                        .filter(brand -> brand.getName() != null && brand.getName().contains(keyword))
                                        .toList();
                            }
                            return Map.of("total", brands.size(), "records", brands);
                        }),

                new McpTool("spectrace_brand_get", "查询品牌详情",
                        "按 id 查询单个品牌。",
                        McpScope.BRAND_READ,
                        McpSchemas.object(McpSchemas.props("id", McpSchemas.integer("品牌 id", 1, null)), "id"),
                        (context, args) -> requireBrand(args.requireLong("id"))),

                new McpTool("spectrace_brand_create", "创建品牌",
                        "创建品牌。name 唯一；status 默认 1（启用），sortOrder 默认 0。",
                        McpScope.BRAND_WRITE,
                        McpSchemas.object(McpSchemas.props(
                                "name", McpSchemas.string("品牌名称，必填且不能与现有品牌重复"),
                                "logo", McpSchemas.string("品牌 logo 地址"),
                                "sortOrder", McpSchemas.integer("展示顺序，越小越靠前"),
                                "status", McpSchemas.integerEnum("1 启用 / 0 停用，默认 1", 0, 1)
                        ), "name"),
                        (context, args) -> {
                            Brand brand = new Brand();
                            brand.setName(args.requireString("name"));
                            brand.setLogo(args.optString("logo"));
                            brand.setSortOrder(args.optInt("sortOrder", 0));
                            brand.setStatus(status(args.optInt("status", 1)));
                            return brandService.save(brand);
                        }),

                new McpTool("spectrace_brand_update", "更新品牌",
                        "按 id 更新品牌，只覆盖传入的字段，未传字段保持不变。",
                        McpScope.BRAND_WRITE,
                        McpSchemas.object(McpSchemas.props(
                                "id", McpSchemas.integer("品牌 id", 1, null),
                                "name", McpSchemas.string("新名称"),
                                "logo", McpSchemas.string("新 logo 地址"),
                                "sortOrder", McpSchemas.integer("展示顺序"),
                                "status", McpSchemas.integerEnum("1 启用 / 0 停用", 0, 1)
                        ), "id"),
                        (context, args) -> {
                            Long id = args.requireLong("id");
                            requireBrand(id);
                            Brand brand = new Brand();
                            brand.setId(id);
                            brand.setName(args.optString("name"));
                            brand.setLogo(args.optString("logo"));
                            brand.setSortOrder(args.optInt("sortOrder", null));
                            Integer status = args.optInt("status", null);
                            brand.setStatus(status == null ? null : status(status));
                            return brandService.save(brand);
                        }),

                new McpTool("spectrace_brand_delete", "删除品牌",
                        "逻辑删除品牌。品牌下仍有系列或机型时请先处理关联数据。",
                        McpScope.BRAND_WRITE,
                        McpSchemas.object(McpSchemas.props("id", McpSchemas.integer("品牌 id", 1, null)), "id"),
                        (context, args) -> {
                            Long id = args.requireLong("id");
                            requireBrand(id);
                            brandService.delete(id);
                            return Map.of("id", id, "deleted", true);
                        })
        );
    }

    private Brand requireBrand(Long id) {
        return brandService.list(null).stream()
                .filter(brand -> brand.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new NotFoundException("品牌不存在：" + id));
    }

    private Integer status(Integer value) {
        if (value != 0 && value != 1) {
            throw new McpArgumentException("status", "status 只能为 0（停用）或 1（启用）");
        }
        return value;
    }
}
