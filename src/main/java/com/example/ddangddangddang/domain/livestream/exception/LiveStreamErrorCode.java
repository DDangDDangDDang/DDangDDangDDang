package com.example.ddangddangddang.domain.livestream.exception;

import com.example.ddangddangddang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum LiveStreamErrorCode implements ErrorCode {

    NOT_FOUND(HttpStatus.NOT_FOUND, "LIVESTREAM_001", "방송을 찾을 수 없습니다."),
    NOT_OWNER(HttpStatus.FORBIDDEN, "LIVESTREAM_002", "방송 주인만 할 수 있는 작업입니다."),
    INVALID_STATUS_TO_START(HttpStatus.CONFLICT, "LIVESTREAM_003", "대기 중인 방송만 시작할 수 있습니다."),
    NOT_LIVE(HttpStatus.CONFLICT, "LIVESTREAM_004", "진행 중인 방송이 아닙니다."),
    ALREADY_LIVE(HttpStatus.CONFLICT, "LIVESTREAM_005", "이미 진행 중인 방송이 있습니다."),
    CONCURRENT_UPDATE(HttpStatus.CONFLICT, "LIVESTREAM_006", "다른 요청이 먼저 처리되었습니다. 다시 시도해 주세요."),
    MEDIA_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "LIVESTREAM_007", "방송 서버와 통신하지 못했습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
