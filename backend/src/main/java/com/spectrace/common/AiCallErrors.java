package com.spectrace.common;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.agentscope.core.model.transport.HttpTransportException;

import javax.net.ssl.SSLException;
import java.io.IOException;
import java.net.ConnectException;
import java.net.UnknownHostException;
import java.net.http.HttpTimeoutException;
import java.util.Locale;
import java.util.concurrent.CancellationException;

/**
 * 把 HTTP 状态码与异常翻译成统一的失败分类。
 *
 * <p>同一类失败在不同调用链上（AgentScope 传输层、HttpClient、绘图接口）抛出的异常类型不同，
 * 分类逻辑集中在这里，面板上的「失败原因分布」才有稳定的口径。</p>
 */
public final class AiCallErrors {

    private static final int MAX_CAUSE_DEPTH = 4;

    private AiCallErrors() {
    }

    public static String fromStatus(int statusCode) {
        if (statusCode == 401 || statusCode == 403) {
            return AiCallFailure.AUTH.name();
        }
        if (statusCode == 429) {
            return AiCallFailure.RATE_LIMIT.name();
        }
        if (statusCode >= 500) {
            return AiCallFailure.SERVER.name();
        }
        if (statusCode >= 400) {
            return AiCallFailure.CLIENT.name();
        }
        return AiCallFailure.UNKNOWN.name();
    }

    public static String fromThrowable(Throwable error) {
        return fromThrowable(error, 0);
    }

    private static String fromThrowable(Throwable error, int depth) {
        if (error == null || depth > MAX_CAUSE_DEPTH) {
            return AiCallFailure.UNKNOWN.name();
        }
        if (error instanceof HttpTransportException httpError && httpError.getStatusCode() != null) {
            return fromStatus(httpError.getStatusCode());
        }
        if (error instanceof HttpTimeoutException) {
            return AiCallFailure.TIMEOUT.name();
        }
        if (error instanceof CancellationException || error instanceof InterruptedException) {
            return AiCallFailure.CANCELLED.name();
        }
        if (error instanceof SSLException || error instanceof ConnectException
                || error instanceof UnknownHostException) {
            return AiCallFailure.CONNECT.name();
        }
        if (error instanceof IOException) {
            return AiCallFailure.CONNECT.name();
        }
        if (error instanceof JsonProcessingException) {
            return AiCallFailure.INVALID_RESPONSE.name();
        }
        // 部分中转会把超时包装成普通 RuntimeException，只能从文案兜底识别
        String message = error.getMessage() == null ? "" : error.getMessage().toLowerCase(Locale.ROOT);
        if (message.contains("timeout") || message.contains("timed out")) {
            return AiCallFailure.TIMEOUT.name();
        }
        return fromThrowable(error.getCause(), depth + 1);
    }
}
