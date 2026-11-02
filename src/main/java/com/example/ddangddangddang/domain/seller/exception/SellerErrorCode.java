package com.example.ddangddangddang.domain.seller.exception;

import com.example.ddangddangddang.global.exception.ErrorCode;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;

@Getter
@RequiredArgsConstructor
public enum SellerErrorCode implements ErrorCode {

    APPLICATION_PENDING(HttpStatus.CONFLICT, "SELLER_001", "판매자 신청을 심사하고 있습니다. 결과가 나온 뒤 다시 신청해 주세요."),
    ALREADY_VERIFIED(HttpStatus.CONFLICT, "SELLER_002", "이미 판매자 인증을 받았습니다."),
    APPLICATION_NOT_FOUND(HttpStatus.NOT_FOUND, "SELLER_003", "판매자 신청을 찾을 수 없습니다."),
    ALREADY_REVIEWED(HttpStatus.CONFLICT, "SELLER_004", "이미 심사한 신청입니다."),
    CANNOT_SUBSCRIBE_SELF(HttpStatus.BAD_REQUEST, "SELLER_005", "자기 자신은 구독할 수 없습니다."),
    CONTENT_NOT_ALLOWED(HttpStatus.BAD_REQUEST, "SELLER_006", "판매자 정보에 사용할 수 없는 내용이 포함되어 있습니다."),
    INVALID_BUSINESS_NUMBER(HttpStatus.BAD_REQUEST, "SELLER_007", "사업자등록번호 10자리를 다시 확인해 주세요."),
    NOT_VERIFIED_SELLER(HttpStatus.FORBIDDEN, "SELLER_008", "판매자 인증을 받은 회원만 상점 정보를 바꿀 수 있습니다."),
    TERMS_OUTDATED(HttpStatus.CONFLICT, "SELLER_009", "판매자 이용약관이 바뀌었습니다. 화면을 새로 고친 뒤 바뀐 약관에 다시 동의해 주세요."),
    IDENTITY_REQUIRED(HttpStatus.BAD_REQUEST, "SELLER_010", "판매자 신청 전에 휴대폰 본인인증을 해 주세요."),
    IDENTITY_NOT_VERIFIED(HttpStatus.BAD_REQUEST, "SELLER_011", "본인인증이 완료되지 않았습니다. 다시 인증해 주세요."),
    IDENTITY_MINOR(HttpStatus.FORBIDDEN, "SELLER_012", "만 19세 이상만 판매자로 등록할 수 있습니다."),
    IDENTITY_ALREADY_USED(HttpStatus.CONFLICT, "SELLER_013", "이미 다른 계정에서 본인인증한 명의입니다. 한 사람은 한 계정에서만 판매할 수 있습니다."),
    IDENTITY_MISMATCH(HttpStatus.CONFLICT, "SELLER_014", "이 계정은 이미 다른 명의로 본인인증했습니다."),
    IDENTITY_GATEWAY_ERROR(HttpStatus.BAD_GATEWAY, "SELLER_015", "본인인증 결과를 확인하지 못했습니다. 잠시 뒤 다시 시도해 주세요."),
    ACCOUNT_HOLDER_MISMATCH(HttpStatus.BAD_REQUEST, "SELLER_016", "예금주는 본인인증한 이름과 같아야 합니다. 본인 명의 계좌를 입력해 주세요."),
    INVALID_BANK_ACCOUNT(HttpStatus.BAD_REQUEST, "SELLER_017", "계좌번호를 다시 확인해 주세요.");

    private final HttpStatus status;
    private final String code;
    private final String message;
}
