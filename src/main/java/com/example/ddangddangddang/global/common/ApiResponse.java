package com.example.ddangddangddang.global.common;

import com.example.ddangddangddang.global.exception.ErrorCode;

public record ApiResponse<T>(boolean success, T data, ErrorResponse error) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null);
    }

    public static ApiResponse<Void> ok() {
        return new ApiResponse<>(true, null, null);
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode) {
        return new ApiResponse<>(false, null, ErrorResponse.of(errorCode));
    }

    public static ApiResponse<Void> fail(ErrorCode errorCode, String message) {
        return new ApiResponse<>(false, null, ErrorResponse.of(errorCode, message));
    }
}