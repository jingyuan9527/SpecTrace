package com.spectrace.config;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * SQLite 列级迁移。
 *
 * <p>{@code schema.sql} 里的 {@code CREATE TABLE IF NOT EXISTS} 无法给已存在的库补列，
 * SQLite 也不支持 {@code ADD COLUMN IF NOT EXISTS}，因此由本组件在启动时按 PRAGMA 比对后逐列补齐，
 * 保证老库升级与新库初始化走同一条路径。</p>
 */
@Slf4j
@Component
@RequiredArgsConstructor
@Order(Ordered.HIGHEST_PRECEDENCE)
public class SqliteSchemaMigration implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;

    @Override
    public void run(ApplicationArguments args) {
        // 只追加列，不改现有列定义；新增列一律允许 NULL，避免老数据升级失败
        ensureColumn("device", "series_id", "BIGINT DEFAULT NULL");
        ensureColumn("device_param_value", "source_url", "VARCHAR(512) DEFAULT NULL");
        ensureColumn("device_param_value", "confidence", "DECIMAL(3,2) DEFAULT NULL");
        ensureColumn("feedback", "handler_note", "TEXT DEFAULT NULL");
        ensureColumn("admin_user", "must_change_password", "TINYINT DEFAULT 0");
        ensureColumn("agent_task", "task_type", "VARCHAR(24) NOT NULL DEFAULT 'DEVICE_DETAIL'");
        ensureColumn("agent_task", "subscription_id", "BIGINT DEFAULT NULL");
        // 索引必须在补列之后创建，放在 schema.sql 里会在老库上因缺列直接中断启动
        ensureIndex("CREATE INDEX IF NOT EXISTS idx_device_series ON device (series_id)");
        flagDefaultPasswordUsers();
    }

    /**
     * 老库升级：把仍在用初始口令的账号标记为「登录后强制改密」。
     *
     * <p>BCrypt 每次加盐不同，无法用 SQL 比对 hash，只能逐条 matches；管理员数量有限，
     * 启动期一次性开销可以接受。已经改过密码的账号不会被标记，避免无谓打扰。</p>
     */
    private void flagDefaultPasswordUsers() {
        try {
            var rows = jdbcTemplate.queryForList(
                    "SELECT id, password FROM admin_user WHERE must_change_password = 0 AND deleted = 0");
            for (var row : rows) {
                Object hash = row.get("password");
                if (hash != null && passwordEncoder.matches(AdminInitializer.DEFAULT_PASSWORD, hash.toString())) {
                    jdbcTemplate.update("UPDATE admin_user SET must_change_password = 1 WHERE id = ?", row.get("id"));
                    log.warn("管理员仍在使用初始密码，已标记为登录后强制修改: id={}", row.get("id"));
                }
            }
        } catch (Exception e) {
            log.warn("初始密码检查失败，跳过（不影响启动）", e);
        }
    }

    private void ensureIndex(String sql) {
        try {
            jdbcTemplate.execute(sql);
        } catch (Exception e) {
            log.warn("索引创建失败（表或列可能不存在）: {}", sql, e);
        }
    }

    private void ensureColumn(String table, String column, String definition) {
        if (!tableExists(table)) {
            log.warn("迁移跳过：表 {} 不存在", table);
            return;
        }
        if (columnsOf(table).contains(column.toLowerCase(Locale.ROOT))) {
            return;
        }
        String sql = "ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition;
        try {
            jdbcTemplate.execute(sql);
            log.info("表结构迁移完成: {}", sql);
        } catch (Exception e) {
            // 并发启动或重复执行时列已存在，属正常情况
            log.warn("表结构迁移失败（可能已存在）: {}", sql, e);
        }
    }

    private boolean tableExists(String table) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(1) FROM sqlite_master WHERE type = 'table' AND name = ?", Integer.class, table);
        return count != null && count > 0;
    }

    private Set<String> columnsOf(String table) {
        Set<String> columns = new HashSet<>();
        jdbcTemplate.query("PRAGMA table_info(" + table + ")", rs -> {
            columns.add(rs.getString("name").toLowerCase(Locale.ROOT));
        });
        return columns;
    }
}
