package com.spectrace.common;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import io.swagger.v3.oas.annotations.Hidden;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.util.StringUtils;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.util.UUID;
import java.util.stream.Collectors;

@Slf4j
@Hidden
@RestControllerAdvice
public class GlobalExceptionHandler {

    /**
     * 业务异常回真实 HTTP 状态码。
     *
     * <p>此前一律 200 + body.code：网关、日志与监控都读不出失败，前端也只能靠 body 判断。
     * 现在 4xx/5xx 与 body.code 保持一致，body 结构不变，老调用方按 body 判断仍然有效。</p>
     */
    @ExceptionHandler(BizException.class)
    public ResponseEntity<R<Void>> handleBiz(BizException e) {
        log.warn("业务异常: code={} message={}", e.getCode(), e.getMessage());
        HttpStatus status = e.getCode() >= 400 && e.getCode() < 600
                ? HttpStatus.resolve(e.getCode())
                : HttpStatus.BAD_REQUEST;
        return ResponseEntity.status(status == null ? HttpStatus.BAD_REQUEST : status)
                .body(R.fail(e.getCode(), e.getMessage()));
    }

    @ExceptionHandler(RateLimitedException.class)
    @ResponseStatus(HttpStatus.TOO_MANY_REQUESTS)
    public R<Void> handleRateLimited(RateLimitedException e) {
        log.warn("请求被限流: {}", e.getMessage());
        return R.fail(e.getCode(), e.getMessage());
    }

    /**
     * 校验失败回真实 400：提交了非法表单在网关与监控侧必须是失败，不能记成 200。
     */
    @ExceptionHandler(BindException.class)
    public ResponseEntity<R<Void>> handleValid(BindException e) {
        FieldError fieldError = e.getBindingResult().getFieldError();
        String msg = fieldError == null ? "参数校验失败" : fieldError.getField() + " " + fieldError.getDefaultMessage();
        log.warn("参数校验失败: {}", msg);
        return ResponseEntity.badRequest().body(R.fail(400, msg));
    }

    /**
     * 参数类型不匹配回真实 400：扫描器乱扫 URL 时日志里不该是一片 200。
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<R<Void>> handleTypeMismatch(MethodArgumentTypeMismatchException e) {
        String required = e.getRequiredType() == null ? "?" : e.getRequiredType().getSimpleName();
        log.warn("请求参数类型不匹配: name={} value={} requiredType={}", e.getName(), e.getValue(), required);
        return ResponseEntity.badRequest()
                .body(R.fail(400, "参数 " + e.getName() + " 类型不正确，应为 " + required));
    }

    /**
     * 请求体反序列化失败几乎都是字段值不合法（典型：发布日期只传了年份 "2025"）。
     * 归为客户端 400 并降为 WARN：ERROR 级堆栈会把日志刷满，而修数据的动作在调用方。
     */
    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<R<Void>> handleUnreadable(HttpMessageNotReadableException e) {
        String detail = "";
        if (e.getMostSpecificCause() instanceof InvalidFormatException format) {
            String field = format.getPath().stream()
                    .map(ref -> ref.getFieldName() != null ? ref.getFieldName() : "[" + ref.getIndex() + "]")
                    .collect(Collectors.joining("."));
            detail = String.format("字段 %s 的值「%s」不合法", StringUtils.hasText(field) ? field : "?",
                    format.getValue());
        }
        log.warn("请求体解析失败: {}", StringUtils.hasText(detail) ? detail : e.getMostSpecificCause().getMessage());
        return ResponseEntity.badRequest().body(R.fail(400, StringUtils.hasText(detail)
                ? detail + "，请检查取值后重试（日期需为 YYYY-MM-DD）"
                : "请求体格式错误，请检查各字段取值（日期需为 YYYY-MM-DD）"));
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public R<Void> handleAccessDenied(AccessDeniedException e) {
        return R.fail(403, "无权限访问");
    }

    /**
     * 资源不存在回真实 404：前端据此把「机型已下架」与「服务异常」分成两种态，
     * 不再把加载失败展示成「不存在」。
     */
    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<R<Void>> handleNotFound(NotFoundException e) {
        log.info("资源不存在: code={} message={}", e.getCode(), e.getMessage());
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(R.fail(e.getCode(), e.getMessage()));
    }

    /**
     * 未预期异常只回错误编号：异常 message 常含 SQL 片段、文件路径与类名，
     * 直接回给客户端等于免费的情报泄漏；排查靠编号在日志里对齐。
     *
     * <p>必须回真实 500：此前回 200 会让网关 5xx 告警、APM 错误率与健康检查全部看不到故障。</p>
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<R<Void>> handleOther(Exception e, HttpServletRequest request) {
        String traceId = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        log.error("未处理异常 traceId={} method={} path={}", traceId, request.getMethod(), request.getRequestURI(), e);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(R.fail(500, "服务器内部错误，请稍后重试（错误编号 " + traceId + "）"));
    }
}
