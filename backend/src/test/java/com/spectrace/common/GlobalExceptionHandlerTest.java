package com.spectrace.common;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.HttpMessageNotReadableException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 请求体反序列化失败的兜底：生产曾因审核表单提交 "2025" 这类年份值触发 500 + ERROR 堆栈。
 * 修复后必须返回 400 与可读的字段级提示，且日志降为 WARN（本测试只锁行为）。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("不合法日期值返回 400 并指明字段与取值")
    void unreadableDateBecomes400WithFieldName() {
        InvalidFormatException format = InvalidFormatException.from(null, "bad", "2025", LocalDate.class);
        format.prependPath(new Object(), "releaseDate");

        R<Void> result = handler.handleUnreadable(
                new HttpMessageNotReadableException("JSON parse error", format, null));

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).contains("releaseDate").contains("2025").contains("YYYY-MM-DD");
    }

    @Test
    @DisplayName("无字段路径的解析失败退化为通用提示，仍为 400")
    void unreadableFallbackStill400() {
        R<Void> result = handler.handleUnreadable(
                new HttpMessageNotReadableException("bad json", new MismatchedInputException(null, "oops") {}, null));

        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).contains("请求体格式错误");
    }
}
