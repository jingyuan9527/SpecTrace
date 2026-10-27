package com.spectrace.common;

import com.fasterxml.jackson.databind.exc.InvalidFormatException;
import com.fasterxml.jackson.databind.exc.MismatchedInputException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.BindException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 失败分支必须回真实 4xx/5xx：回 200 会让网关 5xx 告警、监控错误率与日志巡检全部看不到故障。
 * 本测试锁定 HTTP 状态码与 body.code 一致，同时锁住错误信息不泄漏内部细节。
 */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    @DisplayName("不合法日期值返回 400 并指明字段与取值")
    void unreadableDateBecomes400WithFieldName() {
        InvalidFormatException format = InvalidFormatException.from(null, "bad", "2025", LocalDate.class);
        format.prependPath(new Object(), "releaseDate");

        ResponseEntity<R<Void>> response = handler.handleUnreadable(
                new HttpMessageNotReadableException("JSON parse error", format, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        R<Void> result = response.getBody();
        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).contains("releaseDate").contains("2025").contains("YYYY-MM-DD");
    }

    @Test
    @DisplayName("无字段路径的解析失败退化为通用提示，仍为 400")
    void unreadableFallbackStill400() {
        ResponseEntity<R<Void>> response = handler.handleUnreadable(
                new HttpMessageNotReadableException("bad json", new MismatchedInputException(null, "oops") {}, null));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        R<Void> result = response.getBody();
        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).contains("请求体格式错误");
    }

    @Test
    @DisplayName("@Valid 校验失败回真实 400，不再包成 HTTP 200")
    void validationFailureReturnsReal400() {
        SampleForm form = new SampleForm();
        BeanPropertyBindingResult binding = new BeanPropertyBindingResult(form, "form");
        binding.rejectValue("title", "NotBlank", "不能为空");

        ResponseEntity<R<Void>> response = handler.handleValid(new BindException(binding));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        R<Void> result = response.getBody();
        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).contains("title").contains("不能为空");
    }

    @Test
    @DisplayName("路径参数类型不匹配回真实 400，不再是 HTTP 200")
    void typeMismatchReturnsReal400() {
        MethodArgumentTypeMismatchException e = new MethodArgumentTypeMismatchException(
                "abc", Long.class, "id", null, new NumberFormatException("For input string: \"abc\""));

        ResponseEntity<R<Void>> response = handler.handleTypeMismatch(e);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        R<Void> result = response.getBody();
        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(400);
        assertThat(result.getMessage()).contains("id").contains("Long");
    }

    @Test
    @DisplayName("未预期异常回真实 500，且只回错误编号、不泄漏内部 message")
    void unexpectedErrorHidesInternalDetail() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/public/devices/7");

        ResponseEntity<R<Void>> response = handler.handleOther(
                new IllegalStateException("SQLITE_ERROR: no such column: secret_token"), request);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
        R<Void> result = response.getBody();
        assertThat(result).isNotNull();
        assertThat(result.getCode()).isEqualTo(500);
        assertThat(result.getMessage()).contains("错误编号").doesNotContain("SQLITE_ERROR").doesNotContain("secret_token");
    }

    @Test
    @DisplayName("资源不存在回真实 404，供前端区分「已下架」与「加载失败」")
    void notFoundReturnsReal404() {
        ResponseEntity<R<Void>> response = handler.handleNotFound(new NotFoundException("机型不存在"));

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getCode()).isEqualTo(404);
    }

    /** 只为承载字段级校验错误，不含业务语义。 */
    static class SampleForm {

        private String title;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }
    }
}
