package com.spectrace.common;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.stream.Collectors;

@Slf4j
@Hidden
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BizException.class)
    public R<Void> handleBiz(BizException e) {
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(RateLimitedException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public R<Void> handleRateLimited(RateLimitedException e) {
        log.warn("请求被限流: {}", e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    @ExceptionHandler(BindException.class)
    public R<Void> handleValid(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError == null ? "参数校验失败" : fieldError.getField() + " " + fieldError.getDefaultMessage();
        return R.fail(400, msg);
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public R<Void> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String required = e.getRequiredType() == null ? "?" : e.getRequiredType().getSimpleName();
        log.warn("请求参数类型不匹配: name={} value={} requiredType={}", e.getName(), e.getValue(), required);
        return R.fail(400, "参数 " + e.getName() + " 类型不正确，应为 " + required);
    }

    /**
     * 请求体反序列化失败几乎都是字段值不合法（典型：发布日期只传了年份 "2025"）。
     * 归为客户端 400 并降为 WARN：ERROR 级堆栈会把日志刷满，而修数据的动作在调用方。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public R<Void> handleUnreadable(HttpMessageNotReadableException e) {
        String detail = "";
        if (e.getMostSpecificCause() instanceof InvalidFormatException format) {
            String field = format.getPath().stream()
                    .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                    .collect(Collectors.joining("."));
            detail = String.format("字段 %s 的值「%s」不合法", StringUtils.hasText(field) ? field : "?",
                    format.getValue());
        }
        log.warn("请求体解析失败: {}", StringUtils.hasText(detail) ? detail : e.getMostSpecificCause().getMessage());
        return R.fail(400, StringUtils.hasText(detail)
                ? detail + "，请检查取值后重试（日期需为 YYYY-MM-DD）"
                : "请求体格式错误，请检查各字段取值（日期需为 YYYY-MM-DD）");
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleAccessDenied(AccessDeniedException e) {
        return R.fail(403, "无权限访问");
    }

    @ExceptionHandler(Exception.class)
    public R<Void> handleOther(Exception e) {
        log.error("未处理异常", e);
        return R.fail(500, "服务器内部错误: " + e.getMessage());
    }
}
