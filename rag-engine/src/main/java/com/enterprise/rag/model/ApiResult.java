package com.enterprise.rag.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 统一 API 响应
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ApiResult<T> {

    private int code;
    private String message;
    private T data;

    public static <T> ApiResult<T> ok(T data) {
        return ApiResult.<T>builder().code(0).message("success").data(data).build();
    }

    public static <T> ApiResult<T> ok(String message, T data) {
        return ApiResult.<T>builder().code(0).message(message).data(data).build();
    }

    public static <T> ApiResult<T> fail(String message) {
        return ApiResult.<T>builder().code(-1).message(message).data(null).build();
    }
}
