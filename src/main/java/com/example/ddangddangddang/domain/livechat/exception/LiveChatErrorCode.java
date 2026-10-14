package com.example.ddangddangddang.domain.livechat.exception;

import com.example.ddangddangddang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum LiveChatErrorCode implements ErrorCode {

    // 도배가 감지된 순간 (이때부터 채팅 금지 시작)
    TOO_MANY_MESSAGES(HttpStatus.TOO_MANY_REQUESTS, "LIVECHAT_001", "메시지를 너무 빠르게 보내 30초 동안 채팅이 금지되었습니다."),
    // 채팅 금지 중에 다시 보낸 경우. 응답에는 LiveChatService가 남은 시간을 붙인 문구가 나간다.
    CHAT_MUTED(HttpStatus.TOO_MANY_REQUESTS, "LIVECHAT_002", "도배로 채팅이 금지된 상태입니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
