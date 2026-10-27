package com.spectrace.common;

/**
 * 资源不存在。
 *
 * <p>与普通业务异常区分开：只有「请求的资源确实不存在」才用它，由全局异常处理器回真实 HTTP 404，
 * 让前端与网关能把它和「服务内部错误」「参数不合法」区分开——否则访客会把一次网络故障
 * 误读成「机型已下架」。</p>
 */
public class NotFoundException extends BizException {

    public NotFoundException(String message) {
        super(404, message);
    }
}
