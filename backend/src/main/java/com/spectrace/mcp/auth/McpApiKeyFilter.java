package com.spectrace.mcp.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.spectrace.mcp.config.McpProperties;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * MCP 端点的 API Key 认证。
 *
 * <p>不接入 Spring Security 的账号体系：MCP 的调用方是 Agent 而非后台员工，凭据、权限范围与
 * 吊销周期都不同。Security 层对 {@code /mcp} 放行，由本过滤器做第一道门，解析出的身份随请求属性
 * 传给工具层，未认证请求根本进不了 MCP 握手。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class McpApiKeyFilter extends OncePerRequestFilter {

    /** 请求属性名，同时也是 transport context 的键 */
    public static final String PRINCIPAL_ATTRIBUTE = McpApiKeyFilter.class.getName() + ".principal";

    private static final String HEADER_KEY = "X-MCP-Key";

    private final McpApiKeyService apiKeyService;

    private final McpProperties properties;

    private final ObjectMapper objectMapper;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        if (!properties.isEnabled()) {
            return true;
        }
        String path = request.getRequestURI().substring(request.getContextPath().length());
        String endpoint = properties.getEndpoint();
        return !(path.equals(endpoint) || path.startsWith(endpoint + "/"));
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (HttpMethod.OPTIONS.matches(request.getMethod())) {
            chain.doFilter(request, response);
            return;
        }

        String rawKey = resolveKey(request);
        if (rawKey == null) {
            log.warn("MCP 请求缺少 API Key: {} {}", request.getMethod(), request.getRequestURI());
            writeUnauthorized(response, "缺少 MCP API Key，请在 X-MCP-Key 或 Authorization: Bearer 中提供");
            return;
        }

        try {
            McpAgentPrincipal principal = apiKeyService.authenticate(rawKey);
            request.setAttribute(PRINCIPAL_ATTRIBUTE, principal);
            log.debug("MCP 认证通过: agent={} role={} method={} uri={}",
                    principal.agentName(), principal.role(), request.getMethod(), request.getRequestURI());
            chain.doFilter(request, response);
        } catch (McpAuthenticationException e) {
            log.warn("MCP 认证失败: method={} uri={} reason={}",
                    request.getMethod(), request.getRequestURI(), e.getMessage());
            writeUnauthorized(response, e.getMessage());
        } catch (Exception e) {
            log.error("MCP 认证过程异常: uri={}", request.getRequestURI(), e);
            writeUnauthorized(response, "MCP 认证失败");
        }
    }

    private String resolveKey(HttpServletRequest request) {
        String header = request.getHeader(HEADER_KEY);
        if (header != null && !header.isBlank()) {
            return header.trim();
        }
        String authorization = request.getHeader("Authorization");
        if (authorization != null && authorization.regionMatches(true, 0, "Bearer ", 0, 7)) {
            String token = authorization.substring(7).trim();
            // 后台 JWT 也走 Bearer，这里只接管 MCP 自己的 Key，避免把 JWT 当 API Key 白跑一次查询
            if (token.startsWith(McpApiKeyService.KEY_PREFIX)) {
                return token;
            }
        }
        return null;
    }

    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding("UTF-8");
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", 401);
        body.put("message", message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
