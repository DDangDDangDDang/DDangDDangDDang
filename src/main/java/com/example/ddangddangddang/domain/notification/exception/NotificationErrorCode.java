package com.example.ddangddangddang.domain.notification.exception;

import com.example.ddangddangddang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum NotificationErrorCode implements ErrorCode {

    NOT_FOUND(HttpStatus.NOT_FOUND, "NOTIFICATION_001", "알림을 찾을 수 없습니다."),
    AUCTION_ALERT_UNAVAILABLE(HttpStatus.CONFLICT, "NOTIFICATION_002", "시작 전이거나 진행 중인 경매만 알림을 받을 수 있습니다."),
    OWN_AUCTION_ALERT(HttpStatus.BAD_REQUEST, "NOTIFICATION_003", "내 경매는 알림을 받기로 하지 않아도 결과를 알려 드립니다."),
    INVALID_PUSH_SUBSCRIPTION(HttpStatus.BAD_REQUEST, "NOTIFICATION_004", "브라우저 알림 구독 정보가 올바르지 않습니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
