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
    MEDIA_SERVER_ERROR(HttpStatus.BAD_GATEWAY, "LIVESTREAM_007", "방송 서버와 통신하지 못했습니다."),
    ALREADY_ENDED(HttpStatus.CONFLICT, "LIVESTREAM_008", "종료된 방송은 수정할 수 없습니다."),
    CONTENT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "LIVESTREAM_009", "방송 정보에 사용할 수 없는 내용이 포함되어 있습니다."),
    ACTIVE_STREAM_EXISTS(HttpStatus.CONFLICT, "LIVESTREAM_010", "종료되지 않은 방송이 있습니다. 그 방송을 시작하거나 종료한 뒤 새로 만들어 주세요."),
    SCHEDULE_CHANGE_NOT_ALLOWED(HttpStatus.CONFLICT, "LIVESTREAM_011", "방송 시작 예정 시간은 방송을 시작하기 전에만 바꿀 수 있습니다."),
    INVALID_SCHEDULED_START(HttpStatus.BAD_REQUEST, "LIVESTREAM_012", "방송 시작 예정 시간은 지금부터 30일 이내로 정해 주세요."),
    TOO_MANY_GUEST_JOINS(HttpStatus.TOO_MANY_REQUESTS, "LIVESTREAM_013", "시청 입장 요청이 너무 많습니다. 잠시 후 다시 시도해 주세요."),
    FEATURED_AUCTION_NOT_AVAILABLE(HttpStatus.CONFLICT, "LIVESTREAM_014", "이 방송에 올라간 시작 전·진행 중 경매만 영상 위에 띄울 수 있습니다."),
    INVALID_HASHTAG(HttpStatus.BAD_REQUEST, "LIVESTREAM_015", "해시태그는 한글·영문·숫자·밑줄(_)로 20자까지 쓸 수 있습니다."),
    NOT_VERIFIED_SELLER(HttpStatus.FORBIDDEN, "LIVESTREAM_016", "판매자 인증을 받은 회원만 방송을 만들고 시작할 수 있습니다."),
    FEATURED_AUCTION_LIMIT(HttpStatus.CONFLICT, "LIVESTREAM_017", "영상 위에는 경매를 2개까지 띄울 수 있습니다. 띄운 경매 하나를 내린 뒤 다시 시도해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
