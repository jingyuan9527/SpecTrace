package com.spectrace.mcp.tool;

import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * params 的增量合并与变更摘要。
 *
 * <p>服务端写参数只认「提交即全部」，外部 Agent 想补几个新参数时得先把全部旧键读回来重传，
 * 漏传一个就静默删除。补丁语义与「到底改了哪些键」的判断收在这一个类里，
 * 工具层与单测共用，避免两处口径漂移。</p>
 */
final class McpParamPatch {

    private McpParamPatch() {
    }

    /** 补丁语义：值为空白表示删除该键，其余覆盖；返回合并后的完整参数集合。 */
    static Map<String, String> merge(Map<String, String> current, Map<String, String> patch) {
        Map<String, String> merged = new LinkedHashMap<>(current);
        for (Map.Entry<String, String> entry : patch.entrySet()) {
            if (StringUtils.hasText(entry.getValue())) {
                merged.put(entry.getKey(), entry.getValue().trim());
            } else {
                merged.remove(entry.getKey());
            }
        }
        return merged;
    }

    /** 与落库口径对齐：param_def 里不存在的键、值为空白的键都不会落库（空白等于删除）。 */
    static Map<String, String> effective(Map<String, String> desired, Set<String> definedKeys) {
        Map<String, String> result = new LinkedHashMap<>();
        if (desired == null) {
            return result;
        }
        desired.forEach((key, value) -> {
            if (definedKeys.contains(key) && StringUtils.hasText(value)) {
                result.put(key, value.trim());
            }
        });
        return result;
    }

    /**
     * 变更摘要：列出会新增、覆盖、删除的键，以及被忽略的未知键。
     *
     * <p>只给键不给值，避免一次回执把整段简介、整张参数表原样吐回去。</p>
     */
    static Map<String, Object> diff(Map<String, String> before, Map<String, String> desired,
                                   Set<String> definedKeys) {
        Map<String, String> current = effective(before, definedKeys);
        Map<String, String> after = effective(desired, definedKeys);
        List<String> added = new ArrayList<>();
        List<String> updated = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        List<String> ignored = new ArrayList<>();

        after.forEach((key, value) -> {
            if (!current.containsKey(key)) {
                added.add(key);
            } else if (!Objects.equals(current.get(key), value)) {
                updated.add(key);
            }
        });
        for (String key : current.keySet()) {
            if (!after.containsKey(key)) {
                removed.add(key);
            }
        }
        if (desired != null) {
            for (String key : desired.keySet()) {
                if (!definedKeys.contains(key)) {
                    ignored.add(key);
                }
            }
        }

        Map<String, Object> change = new LinkedHashMap<>();
        change.put("added", added);
        change.put("updated", updated);
        change.put("removed", removed);
        change.put("ignored", ignored);
        return change;
    }
}
