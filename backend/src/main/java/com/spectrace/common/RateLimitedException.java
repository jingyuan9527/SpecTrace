package com.spectrace.common;

/**
 * 限流异常：需要以 HTTP 429 明确告知客户端退避，
 * 区别于其他业务错误（HTTP 200 + 业务码），避免客户端继续无节制重试。
 */
public class RateLimitedException extends BizException {

    public RateLimitedException(String message) {
        super(429, message);
    }
}
