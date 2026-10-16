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
    TOO_MANY_MESSAGES(HttpStatus.TOO_MANY_REQUESTS, "DM_007", "메시지를 너무 빠르게 보내고 있습니다. 잠시 후 다시 보내 주세요."),
    CANNOT_BLOCK_SELF(HttpStatus.BAD_REQUEST, "DM_008", "자기 자신은 차단할 수 없습니다."),
    BLOCKED_BY_ME(HttpStatus.FORBIDDEN, "DM_009", "차단한 상대에게는 메시지를 보낼 수 없습니다. 차단을 해제해 주세요."),
    BLOCKED_BY_OTHER(HttpStatus.FORBIDDEN, "DM_010", "메시지를 보낼 수 없는 상대입니다."),
    INVALID_REPORT_TARGET(HttpStatus.BAD_REQUEST, "DM_011", "이 대화방에서 상대가 보낸 메시지만 신고할 수 있습니다."),
    ALREADY_REPORTED(HttpStatus.CONFLICT, "DM_012", "이미 신고한 메시지입니다."),
    REPORT_NOT_FOUND(HttpStatus.NOT_FOUND, "DM_013", "신고를 찾을 수 없습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
