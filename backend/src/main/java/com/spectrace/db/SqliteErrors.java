package com.spectrace.db;

import java.sql.SQLException;

/**
 * SQLite 错误判定：把「稍后重试就能成功」的写锁冲突从普通异常里挑出来。
 *
 * <p>两类码都要认：{@code SQLITE_BUSY}（等待写锁超时）与 {@code SQLITE_BUSY_SNAPSHOT}（WAL 下读事务
 * 升级为写时快照已失效）。注意后者**不受 busy_timeout 约束**，SQLite 直接报错返回，
 * 这也是并发写在百余毫秒内就失败的原因；是否重试必须由调用方决定。</p>
 */
public final class SqliteErrors {

    private static final int SQLITE_BUSY = 5;

    private static final int SQLITE_BUSY_SNAPSHOT = 517;

    private SqliteErrors() {
    }

    /** 沿 cause 链找 SQLite 写锁冲突；MyBatis/Spring 会把 SQLException 包一层，不能只看最外层。 */
    public static boolean isBusy(Throwable error) {
        for (Throwable cause = error; cause != null && cause.getCause() != cause; cause = cause.getCause()) {
            if (cause instanceof SQLException sql
                    && (sql.getErrorCode() == SQLITE_BUSY || sql.getErrorCode() == SQLITE_BUSY_SNAPSHOT)) {
                return true;
            }
            String message = cause.getMessage();
            if (message != null && (message.contains("SQLITE_BUSY") || message.contains("database is locked"))) {
                return true;
            }
        }
        return false;
    }

    /** 是否是本进程写队列的排队失败（可能被 MyBatis 包成 PersistenceException，需看整条 cause 链）。 */
    public static boolean isWriteBusy(Throwable error) {
        for (Throwable cause = error; cause != null && cause.getCause() != cause; cause = cause.getCause()) {
            if (cause instanceof com.spectrace.common.WriteBusyException) {
                return true;
            }
        }
        return false;
    }

    /** 取最内层异常的首行 message，用于日志与备份失败提示（外层往往是包装类的空 message）。 */
    public static String describe(Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null && cause.getCause() != cause) {
            cause = cause.getCause();
        }
        String message = cause.getMessage();
        if (message == null) {
            return cause.getClass().getSimpleName();
        }
        int lineEnd = message.indexOf('\n');
        return lineEnd < 0 ? message : message.substring(0, lineEnd);
    }
}
