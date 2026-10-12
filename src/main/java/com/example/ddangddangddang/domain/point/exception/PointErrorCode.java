package com.example.ddangddangddang.domain.point.exception;

import com.example.ddangddangddang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum PointErrorCode implements ErrorCode {

    WALLET_NOT_FOUND(HttpStatus.NOT_FOUND, "POINT_001", "포인트 지갑이 존재하지 않습니다."),
    WALLET_ALREADY_EXISTS(HttpStatus.CONFLICT, "POINT_002", "이미 포인트 지갑이 존재합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}