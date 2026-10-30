package com.spectrace.common;

/**
 * 写操作没能拿到 SQLite 写锁：本进程排队超时，或数据库被外部写者（备份 VACUUM、另一个进程）占住。
 *
 * <p>继承 {@link BizException} 以复用全局异常处理，回 503 且 body.code 与 HTTP 状态一致；
 * 调用方拿到 503 重试即可成功，不会被误判成服务故障。MCP 侧会单独映射成 {@code RETRY_LATER}。</p>
 */
public class WriteBusyException extends BizException {

    public WriteBusyException(String message) {
        super(503, message);
    }
}
