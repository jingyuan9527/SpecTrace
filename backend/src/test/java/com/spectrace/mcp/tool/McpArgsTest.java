package com.spectrace.mcp.tool;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Agent 传参的容错边界：JSON 反序列化后数字可能是 Integer/Long/Double，
 * 类型判断错一处就会把正常调用打成 500。
 */
class McpArgsTest {

    @Test
    @DisplayName("整数参数兼容 Integer / Long / 字符串 / 整值小数")
    void readsIntegralNumbers() {
        McpArgs args = new McpArgs(Map.of(
                "a", 1,
                "b", 2L,
                "c", "3",
                "d", 4.0));

        assertThat(args.requireLong("a")).isEqualTo(1L);
        assertThat(args.requireLong("b")).isEqualTo(2L);
        assertThat(args.requireLong("c")).isEqualTo(3L);
        assertThat(args.requireLong("d")).isEqualTo(4L);
    }

    @Test
    @DisplayName("带小数的数字与超范围值给出可读错误，而不是抛类型转换异常")
    void rejectsNonIntegralNumbers() {
        assertThatThrownBy(() -> new McpArgs(Map.of("id", 1.5)).requireLong("id"))
                .isInstanceOf(McpArgumentException.class)
                .hasMessageContaining("必须是整数");

        assertThatThrownBy(() -> new McpArgs(Map.of("id", "abc")).requireLong("id"))
                .isInstanceOf(McpArgumentException.class)
                .hasMessageContaining("id");
    }

    @Test
    @DisplayName("日期只接受 yyyy-MM-dd，错误格式带字段名")
    void parsesDateStrictly() {
        assertThat(new McpArgs(Map.of("date", "2025-09-01")).optDate("date"))
                .isEqualTo(LocalDate.of(2025, 9, 1));

        assertThatThrownBy(() -> new McpArgs(Map.of("date", "2025/09/01")).optDate("date"))
                .isInstanceOf(McpArgumentException.class)
                .hasMessageContaining("yyyy-MM-dd");
    }

    @Test
    @DisplayName("params 映射保留空串（表示删除该参数），但跳过 null 值")
    void readsStringMap() {
        McpArgs args = new McpArgs(Map.of("params", Map.of("battery", "")));

        assertThat(args.optStringMap("params")).containsEntry("battery", "");
    }

    @Test
    @DisplayName("id 数组拒绝非数组与非数字元素")
    void validatesLongArray() {
        assertThat(new McpArgs(Map.of("ids", List.of(1, "2"))).requireLongList("ids"))
                .containsExactly(1L, 2L);

        assertThatThrownBy(() -> new McpArgs(Map.of("ids", List.of("x"))).requireLongList("ids"))
                .isInstanceOf(McpArgumentException.class)
                .hasMessageContaining("整数");
    }

    @Test
    @DisplayName("空白字符串视为未提供，便于 Agent 传空可选字段")
    void blankStringTreatedAsAbsent() {
        McpArgs args = new McpArgs(Map.of("name", "  "));

        assertThat(args.has("name")).isFalse();
        assertThat(args.optString("name")).isNull();
    }
}
