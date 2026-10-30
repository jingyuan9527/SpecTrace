package com.spectrace.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 写队列：并发写在 SQLite 上不是变慢而是随机失败，串行化与「只对写锁冲突重试」是唯一可控的做法。
 */
class SqliteWriteGuardTest {

    private final SqliteWriteGuard guard = new SqliteWriteGuard();

    @Test
    @DisplayName("并发写串行执行：任何时刻只有一个线程在写")
    void serializesWrites() throws Exception {
        AtomicInteger inFlight = new AtomicInteger();
        AtomicInteger maxInFlight = new AtomicInteger();
        List<Thread> threads = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            Thread thread = new Thread(() -> {
                try {
                    guard.execute(() -> {
                        maxInFlight.accumulateAndGet(inFlight.incrementAndGet(), Math::max);
                        // 模拟一次写事务的耗时，放大并发窗口
                        Thread.sleep(10);
                        inFlight.decrementAndGet();
                        return null;
                    });
                } catch (Throwable e) {
                    throw new IllegalStateException(e);
                }
            });
            threads.add(thread);
        }
        threads.forEach(Thread::start);
        for (Thread thread : threads) {
            thread.join();
        }

        assertThat(maxInFlight.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("写锁冲突重试一次后成功")
    void retriesBusyFailure() throws Throwable {
        AtomicInteger attempts = new AtomicInteger();
        String result = guard.execute(() -> {
            if (attempts.incrementAndGet() == 1) {
                throw new SQLException("database is locked", "SQLITE_BUSY", 5);
            }
            return "ok";
        });

        assertThat(result).isEqualTo("ok");
        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("包装在 cause 链里的快照冲突也能识别并重试")
    void retriesWrappedSnapshotConflict() throws Throwable {
        AtomicInteger attempts = new AtomicInteger();
        guard.execute(() -> {
            if (attempts.incrementAndGet() == 1) {
                throw new IllegalStateException("update failed",
                        new SQLException("Another database connection has already written", "SQLITE_BUSY_SNAPSHOT", 517));
            }
            return null;
        });

        assertThat(attempts.get()).isEqualTo(2);
    }

    @Test
    @DisplayName("业务异常不重试，原样抛出")
    void doesNotRetryOtherErrors() {
        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> guard.execute(() -> {
            attempts.incrementAndGet();
            throw new IllegalArgumentException("系列不属于该品牌");
        })).isInstanceOf(IllegalArgumentException.class).hasMessage("系列不属于该品牌");

        assertThat(attempts.get()).isEqualTo(1);
    }

    @Test
    @DisplayName("重试次数耗尽后抛出最后一次异常，不会无限重试")
    void stopsAfterMaxAttempts() {
        AtomicInteger attempts = new AtomicInteger();
        assertThatThrownBy(() -> guard.execute(() -> {
            attempts.incrementAndGet();
            throw new SQLException("database is locked", "SQLITE_BUSY", 5);
        })).isInstanceOf(SQLException.class);

        assertThat(attempts.get()).isEqualTo(3);
    }
}
