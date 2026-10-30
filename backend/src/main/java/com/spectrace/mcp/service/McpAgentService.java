package com.spectrace.mcp.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.spectrace.common.BizException;
import com.spectrace.dto.McpAgentCreateRequest;
import com.spectrace.dto.McpAgentCreatedVO;
import com.spectrace.dto.McpAgentUpdateRequest;
import com.spectrace.dto.McpAgentVO;
import com.spectrace.entity.McpAgent;
import com.spectrace.mapper.McpAgentMapper;
import com.spectrace.mcp.auth.McpApiKeyService;
import com.spectrace.mcp.auth.McpScope;
import com.spectrace.mcp.config.McpProperties;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * MCP Agent 注册表维护。
 *
 * <p>凭据的创建、轮换、停用都在这里收口：工具层只消费身份，不接触明文 Key，
 * 后台接口也无法读回旧 Key。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class McpAgentService {

    private static final Set<String> ALLOWED_ROLES = Set.of("READ_ONLY", "EDITOR", "ADMIN");

    private final McpAgentMapper agentMapper;

    private final McpApiKeyService apiKeyService;

    private final McpProperties properties;

    /**
     * 部署引导：配置了引导 Key 时确保存在对应 Agent。
     *
     * <p>已存在同名 Agent 不覆盖其密钥——覆盖会让每次重启都把客户端配置打回原形；
     * 需要换 Key 时走后台轮换接口。</p>
     */
    @PostConstruct
    public void bootstrap() {
        if (!properties.isEnabled()) {
            return;
        }
        McpProperties.Bootstrap bootstrap = properties.getBootstrap();
        if (!StringUtils.hasText(bootstrap.getAgentName()) || !StringUtils.hasText(bootstrap.getApiKey())) {
            return;
        }
        String name = bootstrap.getAgentName().trim();
        McpAgent existing = findByName(name);
        if (existing != null) {
            log.info("MCP 引导 Agent 已存在，保留现有密钥: name={} prefix={}", name, existing.getApiKeyPrefix());
            return;
        }
        String rawKey = bootstrap.getApiKey().trim();
        if (!rawKey.startsWith(McpApiKeyService.KEY_PREFIX) || rawKey.length() < 23) {
            // 建一个认证必然失败的 Agent 比不建更糟：客户端拿到 Key 却永远 401，排查成本极高
            log.error("MCP 引导 Key 格式不正确，跳过创建 Agent（需 stmcp_ 前缀且至少 23 位）: name={}", name);
            return;
        }

        McpAgent agent = new McpAgent();
        agent.setName(name);
        agent.setDescription("由启动配置 SPECTRACE_MCP_BOOTSTRAP_* 自动创建");
        agent.setRole(normalizeRole(bootstrap.getRole()));
        agent.setScopes(McpScope.join(McpScope.defaultsOf(agent.getRole())));
        agent.setStatus(1);
        agent.setApiKeyHash(apiKeyService.hash(rawKey));
        agent.setApiKeyPrefix(apiKeyService.storedPrefix(rawKey));
        agent.setCallCount(0L);
        agentMapper.insert(agent);
        log.warn("已按启动配置创建 MCP Agent（明文 Key 来自环境变量，不在日志中输出）: name={} role={} scopes={}",
                agent.getName(), agent.getRole(), agent.getScopes());
    }

    public List<McpAgentVO> list() {
        return agentMapper.selectList(Wrappers.<McpAgent>lambdaQuery().orderByAsc(McpAgent::getId))
                .stream().map(this::toVO).toList();
    }

    @Transactional
    public McpAgentCreatedVO create(McpAgentCreateRequest req) {
        String name = req.getName() == null ? "" : req.getName().trim();
        if (!StringUtils.hasText(name)) {
            throw new BizException("Agent 名称不能为空");
        }
        if (findByName(name) != null) {
            throw new BizException("Agent 名称已存在：" + name);
        }

        String role = normalizeRole(req.getRole());
        Set<McpScope> scopes = resolveScopes(req.getScopes(), role);
        String plainKey = apiKeyService.generateKey();

        McpAgent agent = new McpAgent();
        agent.setName(name);
        agent.setDescription(req.getDescription());
        agent.setRole(role);
        agent.setScopes(McpScope.join(scopes));
        agent.setStatus(req.getStatus() == null ? 1 : normalizeStatus(req.getStatus()));
        agent.setApiKeyHash(apiKeyService.hash(plainKey));
        agent.setApiKeyPrefix(apiKeyService.storedPrefix(plainKey));
        agent.setCallCount(0L);
        agentMapper.insert(agent);
        log.info("创建 MCP Agent: id={} name={} role={} scopes={}", agent.getId(), name, role, agent.getScopes());
        return created(agent, plainKey, "密钥仅显示本次，请立即保存；丢失后只能轮换");
    }

    @Transactional
    public McpAgentVO update(Long id, McpAgentUpdateRequest req) {
        McpAgent agent = require(id);
        if (req.getName() != null) {
            String name = req.getName().trim();
            if (!StringUtils.hasText(name)) {
                throw new BizException("Agent 名称不能为空");
            }
            McpAgent sameName = findByName(name);
            if (sameName != null && !sameName.getId().equals(id)) {
                throw new BizException("Agent 名称已存在：" + name);
            }
            agent.setName(name);
        }
        if (req.getDescription() != null) {
            agent.setDescription(req.getDescription());
        }
        if (req.getRole() != null) {
            agent.setRole(normalizeRole(req.getRole()));
        }
        if (req.getScopes() != null) {
            agent.setScopes(McpScope.join(resolveScopes(req.getScopes(), agent.getRole())));
        }
        if (req.getStatus() != null) {
            agent.setStatus(normalizeStatus(req.getStatus()));
        }
        agent.setUpdatedAt(LocalDateTime.now());
        agentMapper.updateById(agent);
        log.info("更新 MCP Agent: id={} name={} role={} scopes={} status={}",
                id, agent.getName(), agent.getRole(), agent.getScopes(), agent.getStatus());
        return toVO(agent);
    }

    @Transactional
    public McpAgentCreatedVO rotateKey(Long id) {
        McpAgent agent = require(id);
        String plainKey = apiKeyService.generateKey();
        agent.setApiKeyHash(apiKeyService.hash(plainKey));
        agent.setApiKeyPrefix(apiKeyService.storedPrefix(plainKey));
        agent.setUpdatedAt(LocalDateTime.now());
        agentMapper.updateById(agent);
        log.warn("轮换 MCP Agent 密钥: id={} name={} 新前缀={}", id, agent.getName(), agent.getApiKeyPrefix());
        return created(agent, plainKey, "旧密钥已立即失效，请同步更新客户端配置");
    }

    @Transactional
    public void delete(Long id) {
        McpAgent agent = require(id);
        agentMapper.deleteById(id);
        log.warn("删除 MCP Agent: id={} name={} prefix={}", id, agent.getName(), agent.getApiKeyPrefix());
    }

    /** 调用计数与最后活跃时间；失败只记 warn，不能因为统计写失败影响工具调用结果 */
    public void recordUsage(Long agentId) {
        if (agentId == null) {
            return;
        }
        try {
            agentMapper.update(null, Wrappers.<McpAgent>lambdaUpdate()
                    .eq(McpAgent::getId, agentId)
                    .set(McpAgent::getLastUsedAt, LocalDateTime.now())
                    .setSql("call_count = call_count + 1"));
        } catch (Exception e) {
            log.warn("MCP Agent 用量统计写入失败: agentId={}", agentId, e);
        }
    }

    private McpAgent require(Long id) {
        McpAgent agent = id == null ? null : agentMapper.selectById(id);
        if (agent == null) {
            throw new BizException("MCP Agent 不存在：" + id);
        }
        return agent;
    }

    private McpAgent findByName(String name) {
        return agentMapper.selectOne(Wrappers.<McpAgent>lambdaQuery()
                .eq(McpAgent::getName, name)
                .last("LIMIT 1"));
    }

    private Set<McpScope> resolveScopes(List<String> requested, String role) {
        if (requested == null || requested.isEmpty()) {
            return McpScope.defaultsOf(role);
        }
        Set<McpScope> scopes = new LinkedHashSet<>();
        for (String value : requested) {
            if (!StringUtils.hasText(value)) {
                continue;
            }
            String normalized = value.trim();
            if (McpScope.WILDCARD.equals(normalized)) {
                return McpScope.all();
            }
            try {
                scopes.add(McpScope.of(normalized));
            } catch (IllegalArgumentException e) {
                throw new BizException("未知的 scope：" + normalized + "，可选值见文档");
            }
        }
        if (scopes.isEmpty()) {
            throw new BizException("scopes 不能为空；不传则按 role 使用默认值");
        }
        return scopes;
    }

    private String normalizeRole(String role) {
        String normalized = StringUtils.hasText(role) ? role.trim().toUpperCase() : "EDITOR";
        if (!ALLOWED_ROLES.contains(normalized)) {
            throw new BizException("role 只能是 READ_ONLY / EDITOR / ADMIN");
        }
        return normalized;
    }

    private Integer normalizeStatus(Integer status) {
        if (!Objects.equals(status, 0) && !Objects.equals(status, 1)) {
            throw new BizException("status 只能为 0（停用）或 1（启用）");
        }
        return status;
    }

    private McpAgentCreatedVO created(McpAgent agent, String plainKey, String warning) {
        McpAgentCreatedVO vo = new McpAgentCreatedVO();
        vo.setAgent(toVO(agent));
        vo.setApiKey(plainKey);
        vo.setWarning(warning);
        return vo;
    }

    private McpAgentVO toVO(McpAgent agent) {
        McpAgentVO vo = new McpAgentVO();
        vo.setId(agent.getId());
        vo.setName(agent.getName());
        vo.setDescription(agent.getDescription());
        vo.setApiKeyPrefix(agent.getApiKeyPrefix());
        vo.setRole(agent.getRole());
        vo.setScopes(McpScope.parse(agent.getScopes()).stream().map(McpScope::value).toList());
        vo.setStatus(agent.getStatus());
        vo.setCallCount(agent.getCallCount());
        vo.setLastUsedAt(agent.getLastUsedAt());
        vo.setCreatedAt(agent.getCreatedAt());
        return vo;
    }
}
