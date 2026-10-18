package com.spectrace.agent.dto;

import lombok.Data;

/**
 * SpringBoot 统一响应体 {@code R<T>}：code=0 表示成功。
 */
@Data
public class ApiResponse<T> {

    private Integer code;
    private String message;
    private T data;

    public boolean isOk() {
        return code != null && code == 0;
    }
}
