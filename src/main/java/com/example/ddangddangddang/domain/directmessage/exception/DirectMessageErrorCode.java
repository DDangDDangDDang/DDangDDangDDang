package com.example.ddangddangddang.domain.directmessage.exception;

import com.example.ddangddangddang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum DirectMessageErrorCode implements ErrorCode {

    ROOM_NOT_FOUND(HttpStatus.NOT_FOUND, "DM_001", "대화방을 찾을 수 없습니다."),
    NOT_MEMBER(HttpStatus.FORBIDDEN, "DM_002", "참여하지 않은 대화방입니다."),
    CANNOT_MESSAGE_SELF(HttpStatus.BAD_REQUEST, "DM_003", "자기 자신과는 대화할 수 없습니다."),
    TARGET_USER_NOT_FOUND(HttpStatus.NOT_FOUND, "DM_004", "대화 상대를 찾을 수 없습니다."),
    CLIENT_MESSAGE_ID_CONFLICT(HttpStatus.CONFLICT, "DM_005", "이미 다른 대화방에서 사용한 메시지 ID입니다."),
    CONTENT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "DM_006", "보낼 수 없는 내용이 포함되어 있습니다."),
    TOO_MANY_MESSAGES(HttpStatus.TOO_MANY_REQUESTS, "DM_007", "메시지를 너무 빠르게 보내고 있습니다. 잠시 후 다시 보내 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
