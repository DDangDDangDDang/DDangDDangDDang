package com.example.ddangddangddang.domain.livebot.exception;

import com.example.ddangddangddang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum LiveBotErrorCode implements ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "LIVEBOT_001", "라이브 봇을 찾을 수 없습니다."),
    INVALID_VIDEO(HttpStatus.BAD_REQUEST, "LIVEBOT_002", "녹화 영상은 500MB 이하의 MP4, WEBM 파일만 올릴 수 있습니다."),
    VIDEO_NOT_UPLOADED(HttpStatus.BAD_REQUEST, "LIVEBOT_003", "녹화 영상이 올라가지 않았습니다. 다시 올려 주세요."),
    NOT_LIVE(HttpStatus.CONFLICT, "LIVEBOT_004", "방송 중인 라이브 봇이 아닙니다."),
    LIVE_NOW(HttpStatus.CONFLICT, "LIVEBOT_005", "방송 중인 라이브 봇은 지울 수 없습니다. 방송을 끈 뒤 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
