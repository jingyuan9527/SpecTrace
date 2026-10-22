package com.spectrace.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * SQLite 每日备份：{@code VACUUM INTO} 生成一致性快照并只保留最近 N 份。
 *
 * <p>VACUUM INTO 由 SQLite 自行完成页级复制，比直接拷贝 .db 文件安全（WAL 模式下也能拿到一致快照）。
 * 备份失败只记录日志，不影响主服务运行。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "spectrace.backup", name = "enabled", havingValue = "true", matchIfMissing = true)
public class BackupService {

    private static final DateTimeFormatter FILE_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss");

    private final JdbcTemplate jdbcTemplate;

    @Value("${spectrace.backup.dir:./data/backups}")
    private String backupDir;

    @Value("${spectrace.backup.keep:7}")
    private int keep;

    /** 默认每天 03:30 执行，可用 SPECTRACE_BACKUP_CRON 覆盖。 */
    @Scheduled(cron = "${spectrace.backup.cron:0 30 3 * * *}")
    public void scheduledBackup() {
        try {
            Path file = backup();
            log.info("SQLite 备份完成: {} ({} bytes)", file.toAbsolutePath(), Files.size(file));
        } catch (Exception e) {
            log.error("SQLite 备份失败，请检查备份目录权限与磁盘空间", e);
        }
    }

    /** 执行一次备份并清理超出保留数量的旧文件，返回新备份文件。 */
    public Path backup() throws IOException {
        Path dir = Path.of(backupDir).toAbsolutePath();
        Files.createDirectories(dir);

        Path target = dir.resolve("spectrace-" + LocalDateTime.now().format(FILE_STAMP) + ".db");
        if (Files.exists(target)) {
            throw new IOException("备份文件已存在: " + target);
        }
        // VACUUM INTO 不支持占位符，路径需内联并对单引号转义
        String sqlPath = target.toString().replace("'", "''");
        jdbcTemplate.execute("VACUUM INTO '" + sqlPath + "'");
        log.info("SQLite 快照已生成: {} ({} bytes)", target, Files.size(target));

        cleanup(dir);
        return target;
    }

    /** 仅保留最近的 keep 份备份，按文件名（含时间戳）倒序。 */
    private void cleanup(Path dir) {
        try (Stream<Path> files = Files.list(dir)) {
            List<Path> backups = files
                    .filter(path -> path.getFileName().toString().startsWith("spectrace-"))
                    .filter(path -> path.getFileName().toString().endsWith(".db"))
                    .sorted(Comparator.comparing((Path path) -> path.getFileName().toString()).reversed())
                    .toList();
            for (int i = keep; i < backups.size(); i++) {
                Files.deleteIfExists(backups.get(i));
                log.info("清理过期备份: {}", backups.get(i).getFileName());
            }
        } catch (Exception e) {
            log.warn("清理过期备份失败: dir={}", dir, e);
        }
    }

}
