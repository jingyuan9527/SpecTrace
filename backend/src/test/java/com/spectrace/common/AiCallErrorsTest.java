package com.spectrace.common;

import io.agentscope.core.model.transport.HttpTransportException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.ConnectException;
import java.net.http.HttpTimeoutException;
import java.util.concurrent.CancellationException;

import static org.assertj.core.api.Assertions.assertThat;

/** 失败分类直接决定后台「失败原因分布」的口径，按状态码 → 异常 → 包装链的优先级锁住行为。 */
class AiCallErrorsTest {

    @Test
    @DisplayName("HTTP 状态码按认证 / 限流 / 服务端 / 请求错误分层")
    void classifiesStatusCodes() {
        assertThat(AiCallErrors.fromStatus(401)).isEqualTo(AiCallFailure.AUTH.name());
        assertThat(AiCallErrors.fromStatus(403)).isEqualTo(AiCallFailure.AUTH.name());
        assertThat(AiCallErrors.fromStatus(429)).isEqualTo(AiCallFailure.RATE_LIMIT.name());
        assertThat(AiCallErrors.fromStatus(500)).isEqualTo(AiCallFailure.SERVER.name());
        assertThat(AiCallErrors.fromStatus(400)).isEqualTo(AiCallFailure.CLIENT.name());
    }

    @Test
    @DisplayName("包装异常按 cause 链取最具体的传输错误")
    void unwrapsCauses() {
        HttpTransportException rateLimited = new HttpTransportException("limited", 429, "too many requests");
        assertThat(AiCallErrors.fromThrowable(new RuntimeException("wrapper", rateLimited)))
                .isEqualTo(AiCallFailure.RATE_LIMIT.name());
    }

    @Test
    @DisplayName("超时 / 取消 / 连接失败分别归类")
    void classifiesNetworkFailures() {
        assertThat(AiCallErrors.fromThrowable(new HttpTimeoutException("timeout")))
                .isEqualTo(AiCallFailure.TIMEOUT.name());
        assertThat(AiCallErrors.fromThrowable(new CancellationException("cancel")))
                .isEqualTo(AiCallFailure.CANCELLED.name());
        assertThat(AiCallErrors.fromThrowable(new ConnectException("refused")))
                .isEqualTo(AiCallFailure.CONNECT.name());
        assertThat(AiCallErrors.fromThrowable(new IOException("broken pipe")))
                .isEqualTo(AiCallFailure.CONNECT.name());
    }

    @Test
    @DisplayName("未知分类降级为「其他错误」，不让前端出现空标签")
    void fallsBackToUnknownLabel() {
        assertThat(AiCallErrors.fromThrowable(new IllegalStateException("boom")))
                .isEqualTo(AiCallFailure.UNKNOWN.name());
        assertThat(AiCallFailure.labelOf("LEGACY_REASON")).isEqualTo(AiCallFailure.UNKNOWN.label());
    }
}
