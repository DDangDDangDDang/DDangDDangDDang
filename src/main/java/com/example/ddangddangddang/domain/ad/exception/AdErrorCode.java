package com.example.ddangddangddang.domain.ad.exception;

import com.example.ddangddangddang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum AdErrorCode implements ErrorCode {
    NOT_FOUND(HttpStatus.NOT_FOUND, "AD_001", "광고를 찾을 수 없습니다."),
    INVALID_IMAGE(HttpStatus.BAD_REQUEST, "AD_002", "광고 이미지는 5MB 이하의 JPG, PNG, WEBP, GIF만 올릴 수 있습니다."),
    IMAGE_NOT_UPLOADED(HttpStatus.BAD_REQUEST, "AD_003", "광고 이미지가 올라가지 않았습니다. 다시 올려 주세요."),
    INVALID_LINK(HttpStatus.BAD_REQUEST, "AD_004", "광고 링크는 http:// 또는 https://로 시작하는 주소여야 합니다.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
