package com.spectrace.mcp.tool;

import com.spectrace.common.NotFoundException;
import com.spectrace.entity.ParamDef;
import com.spectrace.entity.ParamGroup;
import com.spectrace.mapper.ParamDefMapper;
import com.spectrace.mapper.ParamGroupMapper;
import com.spectrace.mcp.auth.McpScope;
import com.spectrace.service.ParamService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 参数元数据维护。
 *
 * <p>paramKey 是 Agent 抓取 Schema、机型写入与对比逻辑的共同主键，改键等于让历史参数值失联；
 * 因此更新时只允许改名称、分组、类型等展示属性，paramKey 不可变。</p>
 */
@Component
@RequiredArgsConstructor
public class ParamTools implements McpToolProvider {

    private static final Set<String> PARAM_TYPES = Set.of("text", "number", "image", "date", "select");

    private final ParamService paramService;

    private final ParamGroupMapper groupMapper;

    private final ParamDefMapper defMapper;

    @Override
    public List<McpTool> tools() {
        return List.of(
                new McpTool("spectrace_param_group_list", "查询参数分组",
                        "返回全部参数分组，按 sortOrder 升序。",
                        McpScope.PARAM_READ,
                        McpSchemas.object(McpSchemas.props()),
                        (context, args) -> paramService.listGroups()),

                new McpTool("spectrace_param_group_create", "创建参数分组",
                        "创建参数分组，name 必填。",
                        McpScope.PARAM_WRITE,
                        McpSchemas.object(McpSchemas.props(
                                "name", McpSchemas.string("分组名称，如「屏幕」"),
                                "sortOrder", McpSchemas.integer("展示顺序，越小越靠前")
                        ), "name"),
                        (context, args) -> {
                            ParamGroup group = new ParamGroup();
                            group.setName(args.requireString("name"));
                            group.setSortOrder(args.optInt("sortOrder", 0));
                            return paramService.saveGroup(group);
                        }),

                new McpTool("spectrace_param_group_update", "更新参数分组",
                        "按 id 更新分组名称或排序。",
                        McpScope.PARAM_WRITE,
                        McpSchemas.object(McpSchemas.props(
                                "id", McpSchemas.integer("分组 id", 1, null),
                                "name", McpSchemas.string("新名称"),
                                "sortOrder", McpSchemas.integer("展示顺序")
                        ), "id"),
                        (context, args) -> {
                            Long id = args.requireLong("id");
                            requireGroup(id);
                            ParamGroup group = new ParamGroup();
                            group.setId(id);
                            group.setName(args.optString("name"));
                            group.setSortOrder(args.optInt("sortOrder", null));
                            return paramService.saveGroup(group);
                        }),

                new McpTool("spectrace_param_group_delete", "删除参数分组",
                        "逻辑删除分组。分组下仍有参数定义时拒绝删除，需先把参数迁到其它分组。",
                        McpScope.PARAM_WRITE,
                        McpSchemas.object(McpSchemas.props("id", McpSchemas.integer("分组 id", 1, null)), "id"),
                        (context, args) -> {
                            Long id = args.requireLong("id");
                            requireGroup(id);
                            List<ParamDef> defs = paramService.listDefs(id, null);
                            if (!defs.isEmpty()) {
                                throw new com.spectrace.common.BizException(
                                        "分组下还有 " + defs.size() + " 个参数定义，请先迁移或删除后再删分组");
                            }
                            paramService.deleteGroup(id);
                            return Map.of("id", id, "deleted", true);
                        }),

                new McpTool("spectrace_param_def_list", "查询参数定义",
                        "返回参数定义列表，可按分组与启用状态筛选。写机型参数前先用它确认 paramKey。",
                        McpScope.PARAM_READ,
                        McpSchemas.object(McpSchemas.props(
                                "groupId", McpSchemas.integer("分组 id"),
                                "enabled", McpSchemas.integerEnum("1 启用 / 0 停用", 0, 1)
                        )),
                        (context, args) -> paramService.listDefs(
                                args.optLong("groupId"), args.optInt("enabled", null))),

                new McpTool("spectrace_param_def_create", "创建参数定义",
                        """
                                创建参数定义。paramKey 是稳定标识，创建后不可修改。
                                paramType 只能是 text / number / image / date / select；
                                select 类型需在 enumOptions 里给出 JSON 数组字符串，如 ["OLED","LCD"]。
                                """,
                        McpScope.PARAM_WRITE,
                        McpSchemas.object(McpSchemas.props(
                                "paramKey", McpSchemas.string("稳定标识，小写下划线，如 screen_size"),
                                "paramName", McpSchemas.string("展示名称，如「屏幕尺寸」"),
                                "groupId", McpSchemas.integer("所属分组 id", 1, null),
                                "paramType", McpSchemas.enumString("参数类型", "text", "number", "image", "date", "select"),
                                "enumOptions", McpSchemas.string("select 类型的候选值，JSON 数组字符串"),
                                "unit", McpSchemas.string("单位，如 inch / mAh"),
                                "sortOrder", McpSchemas.integer("组内展示顺序"),
                                "enabled", McpSchemas.integerEnum("1 启用 / 0 停用，默认 1", 0, 1)
                        ), "paramKey", "paramName", "groupId", "paramType"),
                        (context, args) -> {
                            ParamDef def = new ParamDef();
                            def.setParamKey(args.requireString("paramKey"));
                            def.setParamName(args.requireString("paramName"));
                            def.setGroupId(args.requireLong("groupId"));
                            def.setParamType(paramType(args.requireString("paramType")));
                            def.setEnumOptions(args.optString("enumOptions"));
                            def.setUnit(args.optString("unit"));
                            def.setSortOrder(args.optInt("sortOrder", 0));
                            def.setEnabled(enabled(args.optInt("enabled", 1)));
                            return paramService.saveDef(def);
                        }),

                new McpTool("spectrace_param_def_update", "更新参数定义",
                        "按 id 更新参数名称、分组、类型、单位、排序或启用状态。paramKey 不可修改。",
                        McpScope.PARAM_WRITE,
                        McpSchemas.object(McpSchemas.props(
                                "id", McpSchemas.integer("参数定义 id", 1, null),
                                "paramName", McpSchemas.string("展示名称"),
                                "groupId", McpSchemas.integer("所属分组 id", 1, null),
                                "paramType", McpSchemas.enumString("参数类型", "text", "number", "image", "date", "select"),
                                "enumOptions", McpSchemas.string("select 类型的候选值，JSON 数组字符串"),
                                "unit", McpSchemas.string("单位"),
                                "sortOrder", McpSchemas.integer("组内展示顺序"),
                                "enabled", McpSchemas.integerEnum("1 启用 / 0 停用", 0, 1)
                        ), "id"),
                        (context, args) -> {
                            Long id = args.requireLong("id");
                            ParamDef existing = defMapper.selectById(id);
                            if (existing == null) {
                                throw new NotFoundException("参数定义不存在：" + id);
                            }
                            ParamDef def = new ParamDef();
                            def.setId(id);
                            def.setParamName(args.optString("paramName"));
                            def.setGroupId(args.optLong("groupId"));
                            String paramType = args.optString("paramType");
                            def.setParamType(paramType == null ? null : paramType(paramType));
                            def.setEnumOptions(args.optString("enumOptions"));
                            def.setUnit(args.optString("unit"));
                            def.setSortOrder(args.optInt("sortOrder", null));
                            Integer enabled = args.optInt("enabled", null);
                            def.setEnabled(enabled == null ? null : enabled(enabled));
                            return paramService.saveDef(def);
                        }),

                new McpTool("spectrace_param_def_delete", "删除参数定义",
                        "逻辑删除参数定义。删除后机型上已录入的该参数不再展示。",
                        McpScope.PARAM_WRITE,
                        McpSchemas.object(McpSchemas.props(
                                "id", McpSchemas.integer("参数定义 id", 1, null)), "id"),
                        (context, args) -> {
                            Long id = args.requireLong("id");
                            if (defMapper.selectById(id) == null) {
                                throw new NotFoundException("参数定义不存在：" + id);
                            }
                            paramService.deleteDef(id);
                            return Map.of("id", id, "deleted", true);
                        })
        );
    }

    private ParamGroup requireGroup(Long id) {
        ParamGroup group = groupMapper.selectById(id);
        if (group == null) {
            throw new NotFoundException("参数分组不存在：" + id);
        }
        return group;
    }

    private String paramType(String value) {
        String normalized = value.trim().toLowerCase();
        if (!PARAM_TYPES.contains(normalized)) {
            throw new McpArgumentException("paramType", "paramType 只能是 text / number / image / date / select");
        }
        return normalized;
    }

    private Integer enabled(Integer value) {
        if (value != 0 && value != 1) {
            throw new McpArgumentException("enabled", "enabled 只能为 0（停用）或 1（启用）");
        }
        return value;
    }
}
