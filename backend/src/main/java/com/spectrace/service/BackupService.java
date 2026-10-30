package com.spectrace.service;

import com.spectrace.common.BizException;
import com.spectrace.db.SqliteErrors;
import com.spectrace.db.SqliteWriteGuard;
import com.spectrace.dto.BackupFileVO;
import com.spectrace.dto.BackupOverviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.scheduling.support.CronExpression;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * SQLite 备份：{@code VACUUM INTO} 生成一致性快照，支持定时与手动触发，只保留最近 N 份。
 *
 * <p>VACUUM INTO 由 SQLite 自行完成页级复制，比直接拷贝 .db 文件安全（WAL 模式下也能拿到一致快照）。
 * 备份失败只记录日志，不影响主服务运行。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class BackupService {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");
    private static final String PREFIX = "spectrace-";
    private static final String SUFFIX = ".db";

    private final AlertSender alertSender;
    private final JdbcTemplate jdbcTemplate;
    /** VACUUM 也会独占写锁，和业务写排同一条队，否则备份期间任何写都会撞 SQLITE_BUSY。 */
    private final SqliteWriteGuard writeGuard;

    @Value("${spectrace.backup.enabled:true}")
    private boolean enabled;

    @Value("${spectrace.backup.dir:./data/backups}")
    private String backupDir;

    @Value("${spectrace.backup.keep:7}")
    private int keep;

    @Value("${spectrace.backup.cron:0 30 3 * * *}")
    private String cron;

    /** 默认每天 03:30 执行，可用 SPECTRACE_BACKUP_CRON 覆盖。 */
    @Scheduled(cron = "${spectrace.backup.cron:0 30 3 * * *}")
    public void scheduledBackup() {
        if (!enabled) {
            log.debug("备份已关闭，跳过定时任务");
            return;
        }
        try {
            Path file = backup();
            log.info("SQLite 备份完成: {} ({} bytes)", file.toAbsolutePath(), Files.size(file));
        } catch (Exception e) {
            log.error("SQLite 备份失败，请检查备份目录权限与磁盘空间", e);
            // T104：备份连续失败直接威胁数据安全，必须第一时间告警（同规则 24h 一次）
            alertSender.sendOncePerDay("backup_failed", "SQLite 备份失败：" + e.getMessage() + "，请检查备份目录权限与磁盘空间");
        }
    }

    /** 后台手动触发一次备份；目录不可写等异常直接返回给调用方。 */
    public BackupFileVO trigger() {
        if (!enabled) {
            throw new BizException("备份功能已关闭（SPECTRACE_BACKUP_ENABLED=false）");
        }
        try {
            return toVO(backup());
        } catch (IOException e) {
            log.error("手动备份失败", e);
            throw new BizException("备份失败：" + e.getMessage());
        }
    }

    /** 备份概览：配置、下次执行时间与现有备份列表。 */
    public BackupOverviewVO overview() {
        BackupOverviewVO vo = new BackupOverviewVO();
        vo.setEnabled(enabled);
        vo.setDir(Path.of(backupDir).toAbsolutePath().toString());
        vo.setKeep(keep);
        vo.setNextRunAt(nextRunAt());
        vo.setBackups(list());
        return vo;
    }

    /** 最近一次备份耗时（毫秒），供运维页运行时卡片观察趋势；重启清零。 */
    private volatile long lastBackupDurationMs;

    /** 执行一次备份并清理超出保留数量的旧文件，返回新备份文件。 */
    public Path backup() throws IOException {
        Path dir = Path.of(backupDir).toAbsolutePath();
        Files.createDirectories(dir);

        Path target = dir.resolve(PREFIX + LocalDateTime.now().format(FILE_STAMP) + SUFFIX);
        if (Files.exists(target)) {
            throw new IOException("备份文件已存在: " + target);
        }
        // VACUUM INTO 不支持占位符，路径需内联并对单引号转义
        String sqlPath = target.toString().replace("'", "''");
        long startedAt = System.currentTimeMillis();
        try {
            writeGuard.execute(() -> {
                jdbcTemplate.execute("VACUUM INTO '" + sqlPath + "'");
                return null;
            });
        } catch (Throwable e) {
            // 排队超时也归到备份失败：调用方（定时任务/后台按钮）只关心「这次没备成」
            throw new IOException("SQLite 快照生成失败: " + SqliteErrors.describe(e), e);
        }
        lastBackupDurationMs = System.currentTimeMillis() - startedAt;
        log.info("SQLite 快照已生成: {} ({} bytes, {} ms)", target, Files.size(target), lastBackupDurationMs);

        cleanup(dir);
        return target;
    }

    public long getLastBackupDurationMs() {
        return lastBackupDurationMs;
    }

    /** 现有备份文件，按文件名（含时间戳）倒序。 */
    public List<BackupFileVO> list() {
        Path dir = Path.of(backupDir).toAbsolutePath();
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(this::isBackup)
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .map(this::toVO)
                    .toList();
        } catch (Exception e) {
            log.warn("读取备份列表失败: dir={}", dir, e);
            return List.of();
        }
    }

    private boolean isBackup(Path path) {
        String name = path.getFileName().toString();
        return name.startsWith(PREFIX) && name.endsWith(SUFFIX);
    }

    private BackupFileVO toVO(Path path) {
        BackupFileVO vo = new BackupFileVO();
        vo.setFileName(path.getFileName().toString());
        try {
            vo.setSize(Files.size(path));
            vo.setCreatedAt(LocalDateTime.ofInstant(Files.getLastModifiedTime(path).toInstant(), ZoneId.systemDefault()));
        } catch (IOException e) {
            log.warn("读取备份文件信息失败: {}", path, e);
        }
        return vo;
    }

    /** 仅保留最近的 keep 份备份。 */
    private void cleanup(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> backups = new ArrayList<>(files.filter(this::isBackup)
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList());
            for (int i = keep; i < backups.size(); i++) {
                Files.deleteIfExists(backups.get(i));
                log.info("清理过期备份: {}", backups.get(i).getFileName());
            }
        } catch (Exception e) {
            log.warn("清理过期备份失败: dir={}", dir, e);
        }
    }

    /** 按配置的 cron 计算下次执行时间，表达式非法时不阻断页面展示。 */
    private LocalDateTime nextRunAt() {
        if (!enabled) {
            return null;
        }
        try {
            return CronExpression.parse(cron).next(LocalDateTime.now());
        } catch (IllegalArgumentException e) {
            log.warn("备份 cron 表达式非法: {}", cron);
            return null;
        }
    }
}
