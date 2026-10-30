package com.spectrace.mcp.auth;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.spectrace.entity.McpAgent;
import com.spectrace.mapper.McpAgentMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;

/**
 * MCP API Key 的生成与校验。
 *
 * <p>Key 本身是高熵随机串，用 SHA-256 存储即可：BCrypt 的慢哈希是为低熵口令设计的，
 * 在这里只会让每个 MCP 请求多付一次 CPU。前缀单独成列用于定位，避免全表逐行比对。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpApiKeyService {

    public static final String KEY_PREFIX = "stmcp_";

    /** 前缀列长度：stmcp_ + 9 位随机，碰撞概率足够低且后台可辨认 */
    private static final int STORED_PREFIX_LENGTH = 15;

    private static final int RANDOM_LENGTH = 40;

    /** 去掉 0/O/1/l/I 等易混淆字符，方便人工核对 */
    private static final char[] ALPHABET =
            "ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz23456789".toCharArray();

    private static final SecureRandom RANDOM = new SecureRandom();

    private final McpAgentMapper agentMapper;

    /**
     * 校验明文 Key 并返回 Agent 身份。
     *
     * <p>先按前缀取出候选行再比对哈希，命中后仍做常量时间比较，避免前缀碰撞时泄露时序信息。</p>
     */
    public McpAgentPrincipal authenticate(String rawKey) {
        if (rawKey == null || rawKey.isBlank()) {
            throw new McpAuthenticationException("缺少 MCP API Key");
        }
        String key = rawKey.trim();
        if (!key.startsWith(KEY_PREFIX) || key.length() < STORED_PREFIX_LENGTH + 8) {
            throw new McpAuthenticationException("MCP API Key 格式不正确");
        }

        String prefix = key.substring(0, STORED_PREFIX_LENGTH);
        List<McpAgent> candidates = agentMapper.selectList(Wrappers.<McpAgent>lambdaQuery()
                .eq(McpAgent::getApiKeyPrefix, prefix));
        if (candidates.isEmpty()) {
            throw new McpAuthenticationException("MCP API Key 无效或已停用");
        }

        byte[] expected = hash(key).getBytes(StandardCharsets.UTF_8);
        for (McpAgent agent : candidates) {
            byte[] actual = agent.getApiKeyHash() == null
                    ? new byte[0]
                    : agent.getApiKeyHash().getBytes(StandardCharsets.UTF_8);
            if (!MessageDigest.isEqual(expected, actual)) {
                continue;
            }
            if (agent.getStatus() == null || agent.getStatus() != 1) {
                throw new McpAuthenticationException("MCP API Key 无效或已停用");
            }
            Set<McpScope> scopes = McpScope.parse(agent.getScopes());
            return new McpAgentPrincipal(agent.getId(), agent.getName(), agent.getRole(), scopes);
        }
        throw new McpAuthenticationException("MCP API Key 无效或已停用");
    }

    public String generateKey() {
        StringBuilder builder = new StringBuilder(KEY_PREFIX);
        for (int i = 0; i < RANDOM_LENGTH; i++) {
            builder.append(ALPHABET[RANDOM.nextInt(ALPHABET.length)]);
        }
        return builder.toString();
    }

    public String storedPrefix(String rawKey) {
        return rawKey.substring(0, Math.min(STORED_PREFIX_LENGTH, rawKey.length()));
    }

    public String hash(String rawKey) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(rawKey.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            // JDK 必带 SHA-256，走到这里说明运行环境被裁剪，直接失败比退化为弱哈希安全
            throw new IllegalStateException("当前 JDK 不支持 SHA-256", e);
        }
    }
}
