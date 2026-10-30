package com.spectrace.db;

import lombok.RequiredArgsConstructor;
import org.apache.ibatis.executor.statement.StatementHandler;
import org.apache.ibatis.plugin.Interceptor;
import org.apache.ibatis.plugin.Intercepts;
import org.apache.ibatis.plugin.Invocation;
import org.apache.ibatis.plugin.Signature;
import org.springframework.stereotype.Component;

import java.sql.Statement;

/**
 * 把「单条写语句」也送进写队列。
 *
 * <p>{@link SqliteWriteAspect} 只覆盖事务方法，但项目里有大量不带事务的单条写（MCP 调用日志、
 * 操作日志、订阅 lastRunAt、访问计数……）。它们同样要抢 SQLite 写锁，若放任其插进别的事务中间，
 * 正在升级快照的写事务照样会拿到 SQLITE_BUSY_SNAPSHOT。事务内的写已由切面持锁，这里靠可重入锁零代价跳过。</p>
 */
@Component
@RequiredArgsConstructor
@Intercepts(@Signature(type = StatementHandler.class, method = "update", args = {Statement.class}))
public class SqliteWriteInterceptor implements Interceptor {

    private final SqliteWriteGuard guard;

    @Override
    public Object intercept(Invocation invocation) throws Throwable {
        return guard.execute(invocation::proceed);
    }
}
