package com.example.ddangddangddang.global.common;

import com.example.ddangddangddang.global.exception.ErrorCode;

public record ErrorResponse(String code, String message) {

    public static ErrorResponse of(ErrorCode errorCode) {
        return new ErrorResponse(errorCode.getCode(), errorCode.getMessage());
    }

    public static ErrorResponse of(ErrorCode errorCode, String message) {
        return new ErrorResponse(errorCode.getCode(), message);
    }
}