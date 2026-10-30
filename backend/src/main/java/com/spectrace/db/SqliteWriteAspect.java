package com.spectrace.db;

import lombok.RequiredArgsConstructor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 给所有 {@code @Transactional} 方法套一层进程级写队列。
 *
 * <p>项目里没有声明 {@code readOnly=true} 的事务方法，事务方法即写路径，所以不需要再逐个加注解。</p>
 *
 * <p>顺序必须是最高优先级：锁要在事务开启之前拿到、提交之后释放。若锁落在事务内部，
 * 排队中的线程早已各自建立了读快照，等到写的时候照样撞 SQLITE_BUSY_SNAPSHOT。</p>
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class SqliteWriteAspect {

    private final SqliteWriteGuard guard;

    @Around("@annotation(org.springframework.transaction.annotation.Transactional)")
    public Object serialize(ProceedingJoinPoint joinPoint) throws Throwable {
        return guard.execute(joinPoint::proceed);
    }
}
