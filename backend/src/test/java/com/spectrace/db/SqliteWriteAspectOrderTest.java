package com.spectrace.db;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 锁必须在事务之前拿到、提交之后释放：
 * 锁若落在事务内部，排队线程早已建立了读快照，写的时候照样撞 SQLITE_BUSY_SNAPSHOT。
 */
class SqliteWriteAspectOrderTest {

    private static final List<String> EVENTS = new ArrayList<>();

    @Configuration
    @EnableAspectJAutoProxy
    @EnableTransactionManagement
    static class Config {

        @Bean
        SqliteWriteGuard guard() {
            return new SqliteWriteGuard();
        }

        @Bean
        SqliteWriteAspect aspect(SqliteWriteGuard guard) {
            return new SqliteWriteAspect(guard);
        }

        @Bean
        PlatformTransactionManager transactionManager(SqliteWriteGuard guard) {
            return new RecordingTransactionManager(guard);
        }

        @Bean
        DemoWriter writer() {
            return new DemoWriter();
        }
    }

    /** 只记录事务边界，并顺手检验此刻写锁是否已被持有。 */
    static class RecordingTransactionManager extends AbstractPlatformTransactionManager {

        private final SqliteWriteGuard guard;

        RecordingTransactionManager(SqliteWriteGuard guard) {
            this.guard = guard;
        }

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            EVENTS.add("begin:locked=" + guard.isHeldByCurrentThread());
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            EVENTS.add("commit:locked=" + guard.isHeldByCurrentThread());
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            EVENTS.add("rollback");
        }
    }

    static class DemoWriter {

        @Transactional
        public void write() {
            EVENTS.add("write");
        }
    }

    @Test
    @DisplayName("写锁覆盖整个事务：开启前已持锁，提交后仍持有，方法返回后释放")
    void lockCoversWholeTransaction() {
        EVENTS.clear();
        try (AnnotationConfigApplicationContext context =
                     new AnnotationConfigApplicationContext(Config.class)) {
            SqliteWriteGuard guard = context.getBean(SqliteWriteGuard.class);
            context.getBean(DemoWriter.class).write();

            assertThat(EVENTS).containsExactly("begin:locked=true", "write", "commit:locked=true");
            assertThat(guard.isLocked()).isFalse();
        }
    }
}
