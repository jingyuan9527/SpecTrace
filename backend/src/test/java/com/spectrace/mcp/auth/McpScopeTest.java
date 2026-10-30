package com.spectrace.mcp.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class McpScopeTest {

    @Test
    @DisplayName("* 展开为全部 scope")
    void parsesWildcard() {
        assertThat(McpScope.parse("*")).containsExactlyInAnyOrder(McpScope.values());
        assertThat(McpScope.parse(" * ")).hasSize(McpScope.values().length);
    }

    @Test
    @DisplayName("未知 scope 被忽略，历史脏配置不会让整个 Agent 无法认证")
    void ignoresUnknownScopes() {
        assertThat(McpScope.parse("device:read,unknown:scope,device:write"))
                .containsExactly(McpScope.DEVICE_READ, McpScope.DEVICE_WRITE);
    }

    @Test
    @DisplayName("只读角色只拿读权限；编辑者默认不给纠错处理权")
    void roleDefaults() {
        assertThat(McpScope.defaultsOf("READ_ONLY"))
                .allMatch(scope -> scope.name().endsWith("_READ"))
                .hasSize(7);

        assertThat(McpScope.defaultsOf("EDITOR"))
                .contains(McpScope.DEVICE_WRITE)
                .doesNotContain(McpScope.FEEDBACK_WRITE);

        assertThat(McpScope.defaultsOf("ADMIN")).containsExactlyInAnyOrder(McpScope.values());
    }

    @Test
    @DisplayName("join 与 parse 可往返")
    void roundTripsThroughCsv() {
        String raw = McpScope.join(McpScope.all());
        assertThat(McpScope.parse(raw)).hasSize(McpScope.values().length);
    }
}
