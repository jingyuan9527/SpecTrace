package com.spectrace.mcp.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * paramsPatch 与参数变更回执：漏传即删除是全量覆盖的天性，补丁与回执是给外部 Agent 的护栏。
 */
class McpParamPatchTest {

    private static final Set<String> DEFINED = Set.of("screen_size", "battery", "weight", "cpu");

    @Test
    @DisplayName("补丁只动传入的键，未传的旧键全部保留")
    void mergeKeepsUnmentionedKeys() {
        Map<String, String> current = new LinkedHashMap<>();
        current.put("screen_size", "6.1");
        current.put("battery", "3600");
        current.put("weight", "170");

        Map<String, String> merged = McpParamPatch.merge(current, Map.of("cpu", "A18", "weight", "171"));

        assertThat(merged).containsEntry("screen_size", "6.1")
                .containsEntry("battery", "3600")
                .containsEntry("weight", "171")
                .containsEntry("cpu", "A18");
    }

    @Test
    @DisplayName("补丁里的空白值表示删除该键")
    void mergeTreatsBlankAsDelete() {
        Map<String, String> current = new LinkedHashMap<>();
        current.put("screen_size", "6.1");
        current.put("battery", "3600");

        Map<String, String> merged = McpParamPatch.merge(current, Map.of("battery", "  "));

        assertThat(merged).containsExactly(Map.entry("screen_size", "6.1"));
    }

    @Test
    @DisplayName("变更回执列出新增/覆盖/删除，未知键单列 ignored")
    void diffReportsEachKindOfChange() {
        Map<String, String> before = new LinkedHashMap<>();
        before.put("screen_size", "6.1");
        before.put("battery", "3600");
        before.put("weight", "170");

        Map<String, String> desired = new LinkedHashMap<>();
        desired.put("screen_size", "6.1");
        desired.put("battery", "3700");
        desired.put("cpu", "A18");
        desired.put("unknown_key", "x");

        Map<String, Object> change = McpParamPatch.diff(before, desired, DEFINED);

        assertThat(list(change, "added")).containsExactly("cpu");
        assertThat(list(change, "updated")).containsExactly("battery");
        assertThat(list(change, "removed")).containsExactly("weight");
        assertThat(list(change, "ignored")).containsExactly("unknown_key");
    }

    @Test
    @DisplayName("未传参数时不产生回执，避免回执被误读成「清空了参数」")
    void diffOfNullMeansNoChangeInfo() {
        Map<String, Object> change = McpParamPatch.diff(Map.of("a", "1"), null, DEFINED);

        assertThat(list(change, "added")).isEmpty();
        assertThat(list(change, "removed")).isEmpty();
        assertThat(list(change, "ignored")).isEmpty();
    }

    @SuppressWarnings("unchecked")
    private List<String> list(Map<String, Object> change, String key) {
        return (List<String>) change.get(key);
    }
}
