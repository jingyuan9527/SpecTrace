package com.spectrace.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 启动时打印生效配置（敏感项脱敏）。
 *
 * <p>环境变量分散在 yml、README 表与各 Service 的 {@code @Value} 三处，排查「线上行为与预期不符」
 * 时第一步永远是确认「到底加载了哪套配置」。把生效值打进启动日志，比让人去翻容器环境变量可靠。</p>
 */
@Slf4j
@Component
public class EffectiveConfigLogger {

    @Value("${spectrace.public.rate-limit-qpm:120}")
    private int publicQpm;

    @Value("${spectrace.agent.enabled:true}")
    private boolean agentEnabled;

    @Value("${spectrace.agent.queue:memory}")
    private String agentQueue;

    @Value("${spectrace.agent.auto-approve:false}")
    private boolean autoApprove;

    @Value("${spectrace.view.flush-ms:15000}")
    private long viewFlushMs;

    @Value("${spectrace.backup.dir:./data/backups}")
    private String backupDir;

    @Value("${spectrace.backup.keep:7}")
    private int backupKeep;

    @Value("${spectrace.alert.pending-count:20}")
    private int alertPendingCount;

    @Value("${spectrace.alert.pending-oldest-hours:48}")
    private int alertPendingOldestHours;

    @Value("${spectrace.alert.disk-free-mb:1024}")
    private long alertDiskFreeMb;

    @Value("${spectrace.db.path:./data/spectrace.db}")
    private String dbPath;

    @Value("${spectrace.mcp.enabled:true}")
    private boolean mcpEnabled;

    @Value("${spectrace.mcp.endpoint:/mcp}")
    private String mcpEndpoint;

    @Value("${spectrace.mcp.bootstrap.agent-name:}")
    private String mcpBootstrapAgent;

    @EventListener(ApplicationReadyEvent.class)
    public void logEffectiveConfig() {
        log.info("""
                生效配置：
                  - 数据库: {}
                  - 公开接口限流: {} 次/分钟/IP
                  - 内嵌 Agent: 启用={} 队列={} 自动审核={}
                  - 浏览量落库间隔: {} ms
                  - 备份: 目录={} 保留={} 份
                  - MCP: 启用={} 端点={} 引导Agent={}
                  - 告警: 待审条数>={} 最老滞留>={}h 磁盘剩余<={}MB""",
                dbPath, publicQpm, agentEnabled, agentQueue, autoApprove,
                viewFlushMs, backupDir, backupKeep,
                mcpEnabled, mcpEndpoint, mcpBootstrapAgent.isBlank() ? "(未配置)" : mcpBootstrapAgent,
                alertPendingCount, alertPendingOldestHours, alertDiskFreeMb);
    }
}
