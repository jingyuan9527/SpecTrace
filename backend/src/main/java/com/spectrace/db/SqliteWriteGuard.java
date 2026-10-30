package com.spectrace.db;

import com.spectrace.common.WriteBusyException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

/**
 * 进程级写队列：SQLite 同一时刻只允许一个写者，这里让本进程的写事务排队进入。
 *
 * <p>为什么必须有：一个事务先 SELECT 再写时，SQLite 会在写的那一刻把读快照升级为写事务；
 * 若期间有别的连接提交过，就直接抛 {@code SQLITE_BUSY_SNAPSHOT}，且 busy_timeout 对它无效
 * （实测 3ms 就失败）。所以「并发写」不是变慢，而是随机失败——排队是唯一稳的做法。</p>
 *
 * <p>只锁写：读走各自连接，WAL 下互不阻塞。锁可重入，写方法互相调用不会自锁。</p>
 */
@Slf4j
@Component
public class SqliteWriteGuard {

    /** 排队上限：写事务都是毫秒级，等这么久还没轮到说明有长事务卡住，快速失败比让请求干等更诚实。 */
    private static final long LOCK_TIMEOUT_MS = 15_000;

    /** 兜住外部写者（备份 VACUUM、另一个进程）造成的瞬时冲突；本进程内的写已被锁串行化。 */
    private static final int MAX_ATTEMPTS = 3;

    private static final long RETRY_BACKOFF_MS = 200;

    private final ReentrantLock lock = new ReentrantLock(true);

    /**
     * 在写锁内执行一段写逻辑。
     *
     * <p>重试包在事务外层：调用方（切面）在最外层，每次重试都重新开事务，
     * 失败的那次已经整体回滚，不会留下半截数据。</p>
     */
    public <T> T execute(WriteTask<T> task) throws Throwable {
        for (int attempt = 1; ; attempt++) {
            acquire(attempt);
            try {
                return task.run();
            } catch (Throwable e) {
                if (attempt >= MAX_ATTEMPTS || !SqliteErrors.isBusy(e)) {
                    throw e;
                }
                log.warn("SQLite 写锁冲突，准备第 {} 次重试: {}", attempt, SqliteErrors.describe(e));
            } finally {
                lock.unlock();
            }
            // 退避后重新抢锁：不让几个线程同时重试又同时撞在一起
            sleepBeforeRetry(attempt);
        }
    }

    private void acquire(int attempt) {
        try {
            if (lock.tryLock(LOCK_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                return;
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WriteBusyException("写操作排队被中断，请稍后重试");
        }
        log.warn("等待 SQLite 写锁超时: attempt={} timeoutMs={} queueLength={}",
                attempt, LOCK_TIMEOUT_MS, lock.getQueueLength());
        throw new WriteBusyException("写操作排队超时（" + LOCK_TIMEOUT_MS / 1000 + " 秒），请稍后重试");
    }

    private void sleepBeforeRetry(int attempt) {
        try {
            Thread.sleep(RETRY_BACKOFF_MS * attempt);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new WriteBusyException("写操作排队被中断，请稍后重试");
        }
    }

    /** 诊断用：当前线程是否已持锁（判断某个写是否处在队列保护内）。 */
    boolean isHeldByCurrentThread() {
        return lock.isHeldByCurrentThread();
    }

    /** 诊断用：是否有线程正持锁。 */
    boolean isLocked() {
        return lock.isLocked();
    }

    /** 允许抛受检异常的写逻辑，避免调用方为了塞进 Runnable 而吞掉异常。 */
    @FunctionalInterface
    public interface WriteTask<T> {

        T run() throws Throwable;
    }
}
