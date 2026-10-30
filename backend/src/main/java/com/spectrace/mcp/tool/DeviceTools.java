package com.spectrace.mcp.tool;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.common.NotFoundException;
import com.spectrace.dto.DeviceDetailVO;
import com.spectrace.dto.DeviceSaveRequest;
import com.spectrace.entity.Device;
import com.spectrace.mapper.DeviceMapper;
import com.spectrace.mcp.auth.McpScope;
import com.spectrace.mcp.config.McpProperties;
import com.spectrace.service.DeviceService;
import com.spectrace.service.ParamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 机型维护（含参数值与存储版本）。
 *
 * <p>更新时先读旧记录再补齐未传字段：DeviceService.save 对 status 有「缺省置 1」的行为，
 * 若把 Agent 没传的 status 当成 null 直接透传，一次改简介就会把下架机型悄悄上架。</p>
 */
@Component
@RequiredArgsConstructor
public class DeviceTools implements McpToolProvider {

    private final DeviceService deviceService;

    private final DeviceMapper deviceMapper;

    private final McpProperties properties;

    /** 参数键集合决定 paramsPatch 的合并口径与 ignored 列表，必须与落库用的是同一份定义。 */
    private final ParamService paramService;

    /** 机型字段原样回传再挂回执时需要它做一次结构化转换。 */
    private final ObjectMapper objectMapper;

    @Override
    public List<McpTool> tools() {
        return List.of(
                new McpTool("spectrace_device_list", "查询机型列表",
                        """
                                分页查询机型，支持按品牌、系列、发布年份、上架状态与名称关键字筛选。
                                返回 device 主表字段；参数与存储版本需再调 spectrace_device_get。
                                seriesId 传 0 表示「未归类」。
                                """,
                        McpScope.DEVICE_READ,
                        McpSchemas.object(McpSchemas.props(
                                "current", McpSchemas.integer("页码，从 1 开始，默认 1"),
                                "size", McpSchemas.integer("每页条数，默认 10，上限由服务端配置"),
                                "brandId", McpSchemas.integer("品牌 id"),
                                "seriesId", McpSchemas.integer("系列 id；0 表示未归类"),
                                "year", McpSchemas.integer("发布年份，如 2025"),
                                "keyword", McpSchemas.string("机型名称关键字"),
                                "status", McpSchemas.integerEnum("1 上架 / 0 下架；不传返回全部（后台口径）", 0, 1)
                        )),
                        (context, args) -> {
                            McpPageQuery page = McpPageQuery.of(args, properties.getMaxPageSize());
                            var result = deviceService.page(page.current(), page.size(),
                                    args.optLong("brandId"), args.optLong("seriesId"),
                                    args.optInt("year", null), args.optString("keyword"),
                                    args.optInt("status", null));
                            return Map.of(
                                    "total", result.getTotal(),
                                    "current", result.getCurrent(),
                                    "size", result.getSize(),
                                    "records", result.getRecords());
                        }),

                new McpTool("spectrace_device_get", "查询机型详情",
                        """
                                返回机型主表、品牌、系列、全部参数值（paramKey -> 值）、字段级溯源、
                                存储版本与价格，以及同系列机型序列。
                                """,
                        McpScope.DEVICE_READ,
                        McpSchemas.object(McpSchemas.props("id", McpSchemas.integer("机型 id", 1, null)), "id"),
                        (context, args) -> {
                            Long id = args.requireLong("id");
                            DeviceDetailVO detail = deviceService.detail(id);
                            if (detail == null || detail.getDevice() == null) {
                                throw new NotFoundException("机型不存在：" + id);
                            }
                            return detail;
                        }),

                new McpTool("spectrace_device_create", "创建机型",
                        """
                                创建机型并可选写入参数值与存储版本。
                                name + brandId 必填；seriesId 必须属于同一品牌。
                                params 是 paramKey -> 字符串值，键必须存在于 param_def，否则静默忽略；
                                不确定键时先调 spectrace_param_def_list。
                                status 默认 1（上架）。
                                """,
                        McpScope.DEVICE_WRITE,
                        McpSchemas.object(McpSchemas.props(
                                "name", McpSchemas.string("机型名称，必填"),
                                "brandId", McpSchemas.integer("品牌 id，必填", 1, null),
                                "seriesId", McpSchemas.integer("系列 id，可空；必须属于同一品牌"),
                                "releaseDate", McpSchemas.string("发布日期，格式 yyyy-MM-dd"),
                                "thumbnail", McpSchemas.string("机型图地址"),
                                "description", McpSchemas.string("机型简介"),
                                "status", McpSchemas.integerEnum("1 上架 / 0 下架，默认 1", 0, 1),
                                "params", McpSchemas.stringMap("paramKey -> 参数字符串值"),
                                "variants", McpSchemas.variantArray("存储版本与价格")
                        ), "name", "brandId"),
                        (context, args) -> {
                            DeviceSaveRequest req = buildCreate(args);
                            return deviceService.save(req, context.auditAdminId(), context.auditAdminName());
                        }),

                new McpTool("spectrace_device_update", "更新机型",
                        """
                                按 id 更新机型，只覆盖传入的字段。
                                params 是「全量覆盖」：提交的键集合即最终参数集合，未提交或值为空白的键会被删除；
                                只想补几个参数时改用 paramsPatch（paramKey -> 值，值为空白表示删除该键），它只动传入的键。
                                params 与 paramsPatch 不能同时传；都不传表示不改动已有参数。variants 同理：不传不动，传 [] 清空。
                                返回体在机型字段之外附带 paramsChange（added / updated / removed / ignored 四个键列表），
                                removed 非空说明这次真的删了参数，批量补参前可先传 dryRun=true 预览。
                                seriesId 传 0 表示取消归类。
                                清空 thumbnail / description / releaseDate 请先下线机型再重建，接口不支持置空。
                                """,
                        McpScope.DEVICE_WRITE,
                        McpSchemas.object(McpSchemas.props(
                                "id", McpSchemas.integer("机型 id，必填", 1, null),
                                "name", McpSchemas.string("新名称"),
                                "brandId", McpSchemas.integer("新品牌 id", 1, null),
                                "seriesId", McpSchemas.integer("新系列 id；0 表示取消归类"),
                                "releaseDate", McpSchemas.string("发布日期 yyyy-MM-dd"),
                                "thumbnail", McpSchemas.string("机型图地址"),
                                "description", McpSchemas.string("机型简介"),
                                "status", McpSchemas.integerEnum("1 上架 / 0 下架", 0, 1),
                                "params", McpSchemas.stringMap("paramKey -> 参数值；全量覆盖，未提交的键会被删除"),
                                "paramsPatch", McpSchemas.stringMap("paramKey -> 参数值；只改动传入的键，空白值表示删除该键"),
                                "variants", McpSchemas.variantArray("存储版本与价格；全量覆盖"),
                                "dryRun", McpSchemas.bool("true 只返回参数变更预览（paramsChange），不落库")
                        ), "id"),
                        (context, args) -> updateDevice(context, args)),

                new McpTool("spectrace_device_delete", "删除机型",
                        "逻辑删除机型，同时物理清理其参数值与存储版本。",
                        McpScope.DEVICE_WRITE,
                        McpSchemas.object(McpSchemas.props("id", McpSchemas.integer("机型 id", 1, null)), "id"),
                        (context, args) -> {
                            Long id = args.requireLong("id");
                            Device existing = deviceMapper.selectById(id);
                            if (existing == null) {
                                throw new NotFoundException("机型不存在：" + id);
                            }
                            deviceService.delete(id, context.auditAdminId(), context.auditAdminName());
                            return Map.of("id", id, "deleted", true);
                        }),

                new McpTool("spectrace_device_batch_update_status", "批量上下架机型",
                        "批量把机型置为 1 上架或 0 下架。只改状态，不触碰参数与版本。",
                        McpScope.DEVICE_WRITE,
                        McpSchemas.object(McpSchemas.props(
                                "ids", McpSchemas.longArray("机型 id 列表，不能为空"),
                                "status", McpSchemas.integerEnum("1 上架 / 0 下架", 0, 1)
                        ), "ids", "status"),
                        (context, args) -> {
                            List<Long> ids = args.requireLongList("ids");
                            int status = args.requireInt("status");
                            int updated = deviceService.batchUpdateStatus(ids, status,
                                    context.auditAdminId(), context.auditAdminName());
                            return Map.of("requested", ids.size(), "updated", updated, "status", status);
                        })
        );
    }

    private DeviceSaveRequest buildCreate(McpArgs args) {
        DeviceSaveRequest req = new DeviceSaveRequest();
        req.setName(args.requireString("name"));
        req.setBrandId(args.requireLong("brandId"));
        Long seriesId = args.optLong("seriesId");
        req.setSeriesId(seriesId == null || seriesId == 0 ? null : seriesId);
        req.setReleaseDate(args.optDate("releaseDate"));
        req.setThumbnail(args.optString("thumbnail"));
        req.setDescription(args.optString("description"));
        req.setStatus(status(args.optInt("status", 1)));
        req.setParams(args.optStringMap("params"));
        req.setVariants(McpPayloads.toVariants(args.optObjectList("variants")));
        return req;
    }

    /**
     * 更新机型：先算参数变更，再决定落库还是只返回预览。
     *
     * <p>回执里带 paramsChange 是刻意的——全量覆盖下「少传一个键」等于静默删除，
     * 调用方必须能从返回值直接看到 removed 非空。</p>
     */
    private Map<String, Object> updateDevice(McpToolContext context, McpArgs args) {
        Long id = args.requireLong("id");
        Device existing = requireDevice(id);
        boolean dryRun = Boolean.TRUE.equals(args.optBoolean("dryRun", false));
        Map<String, String> desired = resolveParams(args, existing);
        Map<String, Object> change = desired == null ? null : McpParamPatch.diff(
                deviceService.paramValuesOf(id), desired, paramService.keyToDefMap().keySet());

        if (dryRun) {
            return withParamsChange(existing, change, true);
        }
        Device saved = deviceService.save(buildUpdate(args, existing, desired),
                context.auditAdminId(), context.auditAdminName());
        return withParamsChange(saved, change, false);
    }

    /** params 与 paramsPatch 互斥：一个全量覆盖、一个增量合并，同时传必然是调用方理解错了语义。 */
    private Map<String, String> resolveParams(McpArgs args, Device existing) {
        boolean hasParams = args.has("params");
        boolean hasPatch = args.has("paramsPatch");
        if (hasParams && hasPatch) {
            throw new McpArgumentException("paramsPatch", "params 与 paramsPatch 不能同时传：前者全量覆盖，后者只改动传入的键");
        }
        if (hasParams) {
            // {} 表示清空全部参数，与「不传」的语义不同，不能被折叠掉
            return args.optStringMap("params");
        }
        if (hasPatch) {
            return McpParamPatch.merge(deviceService.paramValuesOf(existing.getId()), args.optStringMap("paramsPatch"));
        }
        return null;
    }

    private Device requireDevice(Long id) {
        Device device = deviceMapper.selectById(id);
        if (device == null) {
            throw new NotFoundException("机型不存在：" + id);
        }
        return device;
    }

    /**
     * 机型字段原样返回，只额外挂 paramsChange / dryRun：保持老调用方按设备字段取值的兼容性。
     */
    private Map<String, Object> withParamsChange(Device device, Map<String, Object> change, boolean dryRun) {
        Map<String, Object> body = new LinkedHashMap<>(
                objectMapper.convertValue(device, new TypeReference<Map<String, Object>>() {
                }));
        if (change != null) {
            body.put("paramsChange", change);
        }
        if (dryRun) {
            body.put("dryRun", true);
        }
        return body;
    }

    private DeviceSaveRequest buildUpdate(McpArgs args, Device existing, Map<String, String> params) {
        DeviceSaveRequest req = new DeviceSaveRequest();
        req.setId(existing.getId());
        req.setName(args.has("name") ? args.requireString("name") : existing.getName());
        req.setBrandId(args.has("brandId") ? args.requireLong("brandId") : existing.getBrandId());
        if (args.has("seriesId")) {
            Long seriesId = args.requireLong("seriesId");
            req.setSeriesId(seriesId == 0 ? null : seriesId);
        } else {
            req.setSeriesId(existing.getSeriesId());
        }
        req.setReleaseDate(args.has("releaseDate") ? args.optDate("releaseDate") : existing.getReleaseDate());
        req.setThumbnail(args.has("thumbnail") ? args.optString("thumbnail") : existing.getThumbnail());
        req.setDescription(args.has("description") ? args.optString("description") : existing.getDescription());
        req.setStatus(args.has("status") ? status(args.requireInt("status")) : existing.getStatus());
        // null 表示不改动参数，非 null（含空 Map）都按提交集合覆盖
        req.setParams(params);
        req.setVariants(args.has("variants") ? McpPayloads.toVariants(args.optObjectList("variants")) : null);
        return req;
    }

    private Integer status(Integer value) {
        if (value != 0 && value != 1) {
            throw new McpArgumentException("status", "status 只能为 0（下架）或 1（上架）");
        }
        return value;
    }
}
