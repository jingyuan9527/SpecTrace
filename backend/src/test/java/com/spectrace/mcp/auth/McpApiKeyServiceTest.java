package com.spectrace.mcp.auth;

import com.spectrace.entity.McpAgent;
import com.spectrace.mapper.McpAgentMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class McpApiKeyServiceTest {

    private final McpAgentMapper agentMapper = mock(McpAgentMapper.class);

    private final McpApiKeyService service = new McpApiKeyService(agentMapper);

    @Test
    @DisplayName("生成的 Key 带固定前缀，哈希稳定且不可反推")
    void generatesStableHash() {
        String key = service.generateKey();

        assertThat(key).startsWith(McpApiKeyService.KEY_PREFIX).hasSize(46);
        assertThat(service.hash(key)).hasSize(64).isEqualTo(service.hash(key));
        assertThat(service.storedPrefix(key)).hasSize(15).startsWith(McpApiKeyService.KEY_PREFIX);
        assertThat(service.hash(key)).isNotEqualTo(key);
    }

    @Test
    @DisplayName("正确 Key 返回身份与授权范围")
    void authenticatesValidKey() {
        String key = service.generateKey();
        when(agentMapper.selectList(any())).thenReturn(List.of(agent(key, 1)));

        McpAgentPrincipal principal = service.authenticate(key);

        assertThat(principal.agentName()).isEqualTo("claude");
        assertThat(principal.scopes()).containsExactly(McpScope.DEVICE_READ, McpScope.DEVICE_WRITE);
    }

    @Test
    @DisplayName("停用的 Agent 即使 Key 正确也被拒绝")
    void rejectsDisabledAgent() {
        String key = service.generateKey();
        when(agentMapper.selectList(any())).thenReturn(List.of(agent(key, 0)));

        assertThatThrownBy(() -> service.authenticate(key))
                .isInstanceOf(McpAuthenticationException.class);
    }

    @Test
    @DisplayName("前缀相同但哈希不匹配时拒绝")
    void rejectsWrongKey() {
        String stored = service.generateKey();
        when(agentMapper.selectList(any())).thenReturn(List.of(agent(stored, 1)));

        // 手工构造同前缀但不同随机段的 Key，覆盖「按前缀命中候选行后仍需比对哈希」的分支
        String forged = stored.substring(0, 15) + "x".repeat(31);

        assertThatThrownBy(() -> service.authenticate(forged))
                .isInstanceOf(McpAuthenticationException.class);
    }

    @Test
    @DisplayName("格式不对的 Key 在查库前就拒绝")
    void rejectsMalformedKey() {
        assertThatThrownBy(() -> service.authenticate("wrong"))
                .isInstanceOf(McpAuthenticationException.class)
                .hasMessageContaining("格式");

        assertThatThrownBy(() -> service.authenticate(" "))
                .isInstanceOf(McpAuthenticationException.class)
                .hasMessageContaining("缺少");
    }

    private McpAgent agent(String plainKey, int status) {
        McpAgent agent = new McpAgent();
        agent.setId(1L);
        agent.setName("claude");
        agent.setRole("EDITOR");
        agent.setApiKeyPrefix(service.storedPrefix(plainKey));
        agent.setApiKeyHash(service.hash(plainKey));
        agent.setScopes("device:read,device:write");
        agent.setStatus(status);
        return agent;
    }
}
