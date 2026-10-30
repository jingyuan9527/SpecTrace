package com.spectrace.mcp.config;

import com.spectrace.mcp.auth.McpApiKeyFilter;
import com.spectrace.mcp.tool.McpToolRegistry;
import io.modelcontextprotocol.common.McpTransportContext;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.server.McpSyncServer;
import io.modelcontextprotocol.server.transport.WebMvcStreamableServerTransportProvider;
import io.modelcontextprotocol.spec.McpSchema;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerResponse;

import java.time.Duration;
import java.util.Map;

/**
 * MCP 服务装配。
 *
 * <p>用 Streamable HTTP 而不是 stdio：SpecTrace 是常驻 Web 应用，stdio 需要为每个客户端再起一个
 * 子进程，既重复部署又拿不到同一份 SQLite 写连接。Streamable HTTP 是当前 MCP 规范推荐的可远程
 * 传输方式，Claude Desktop / Cursor / Cherry Studio 均支持。</p>
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "spectrace.mcp", name = "enabled", havingValue = "true", matchIfMissing = true)
public class McpServerConfig {

    private final McpProperties properties;

    private final McpToolRegistry toolRegistry;

    @Bean
    public WebMvcStreamableServerTransportProvider mcpTransportProvider() {
        return WebMvcStreamableServerTransportProvider.builder()
                .mcpEndpoint(properties.getEndpoint())
                // 长连接保活：反向代理常在 60s 空闲后断开，主动心跳让客户端不必重建会话
                .keepAliveInterval(Duration.ofSeconds(30))
                // 认证已在 Filter 完成，这里只把身份从 Servlet 请求带进 MCP 传输上下文
                .contextExtractor(request -> {
                    Object principal = request.servletRequest()
                            .getAttribute(McpApiKeyFilter.PRINCIPAL_ATTRIBUTE);
                    return principal == null
                            ? McpTransportContext.EMPTY
                            : McpTransportContext.create(Map.of(McpApiKeyFilter.PRINCIPAL_ATTRIBUTE, principal));
                })
                .build();
    }

    @Bean
    public RouterFunction<ServerResponse> mcpRouterFunction(WebMvcStreamableServerTransportProvider provider) {
        return provider.getRouterFunction();
    }

    @Bean(destroyMethod = "close")
    public McpSyncServer mcpServer(WebMvcStreamableServerTransportProvider provider) {
        McpSyncServer server = McpServer.sync(provider)
                .serverInfo(properties.getServerName(), properties.getServerVersion())
                // 批量审核等写操作可能跑几十秒，默认超时对慢工具偏紧
                .requestTimeout(Duration.ofSeconds(60))
                .instructions(instructions())
                .capabilities(McpSchema.ServerCapabilities.builder().tools(true).build())
                .tools(toolRegistry.specifications())
                .build();
        log.info("MCP 服务已启动: endpoint={} server={}/{} tools={}",
                properties.getEndpoint(), properties.getServerName(), properties.getServerVersion(),
                toolRegistry.toolNames().size());
        return server;
    }

    /**
     * 这段文字会进入 Agent 的系统提示，只写「易踩的语义」：
     * 参数全量覆盖、删除是逻辑删除、写操作需要哪些 scope，避免 Agent 靠试错学习。
     */
    private String instructions() {
        return """
                SpecTrace 是手机参数对比平台。这里暴露的是运营数据维护能力，不是公开查询接口。
                约定：
                1. 所有写操作都会落 operation_log，adminName 记为 mcp:<agent>，可被后台追溯。
                2. 机型参数以 paramKey -> 字符串值传入，写入语义是「全量覆盖」：提交的键集合即最终参数集合，
                   未提交或值为空白的键视为删除；params 传 null 表示不改动。
                3. variants 同理：null 不改动，空数组清空。
                4. 删除品牌/系列/机型均为逻辑删除；系列下仍有在册机型时删除会被拒绝。
                5. 机型 status：1 上架（对外可见），0 下架（对外 404）。
                6. 不确定参数键时先调 param_def_list，不要凭空造 key。
                7. 权限不足返回 FORBIDDEN，参数错误返回 INVALID_ARGUMENT，可直接按 message 修正后重试。
                """;
    }
}
