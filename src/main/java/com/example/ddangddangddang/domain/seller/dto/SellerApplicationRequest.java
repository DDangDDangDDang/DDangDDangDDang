package com.example.ddangddangddang.domain.seller.dto;

import com.example.ddangddangddang.domain.seller.entity.SellerApplication;
import com.example.ddangddangddang.domain.seller.entity.SellerTerms;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 판매자 신청 (반려된 뒤 다시 신청할 때도 같은 요청) */
public record SellerApplicationRequest(
        @NotBlank(message = "상점 이름을 입력해 주세요.")
        @Size(max = SellerApplication.STORE_NAME_MAX_LENGTH, message = "상점 이름은 30자 이하입니다.")
        String storeName,

        @NotBlank(message = "판매자 소개를 입력해 주세요.")
        @Size(max = SellerApplication.INTRODUCTION_MAX_LENGTH, message = "판매자 소개는 500자 이하입니다.")
        String introduction,

        // 사업자 판매자만: 사업자등록번호 10자리 (하이픈 있어도 됨). 개인 판매자는 비워 둔다.
        @Size(max = 20, message = "사업자등록번호 10자리를 다시 확인해 주세요.")
        String businessNumber,

        // 정산 계좌. 예금주는 본인인증 실명과 같아야 한다. (다르면 SELLER_016)
        @NotBlank(message = "정산받을 은행을 골라 주세요.")
        @Pattern(regexp = "^\\d{3}$", message = "은행 코드는 숫자 3자리입니다.")
        String bankCode,

        @NotBlank(message = "계좌번호를 입력해 주세요.")
        @Pattern(regexp = "^[0-9-]{10,20}$", message = "계좌번호는 숫자와 하이픈(-)으로 10~20자입니다.")
        String accountNumber,

        @NotBlank(message = "예금주를 입력해 주세요.")
        @Size(max = SellerApplication.ACCOUNT_HOLDER_MAX_LENGTH, message = "예금주는 30자 이하입니다.")
        String accountHolder,

        // 반품·교환 받을 주소와 고객 문의 연락처. 인증되면 판매자 프로필에 공개된다.
        @NotBlank(message = "반품·교환 받을 주소를 입력해 주세요.")
        @Size(max = SellerApplication.RETURN_ADDRESS_MAX_LENGTH, message = "반품·교환 주소는 200자 이하입니다.")
        String returnAddress,

        @NotBlank(message = "고객 문의 연락처를 입력해 주세요.")
        @Pattern(regexp = SellerApplicationRequest.CONTACT_PHONE_PATTERN, message = "연락처는 하이픈 없이 숫자만 입력해 주세요. (예: 0212345678)")
        String contactPhone,

        // 판매자 이용약관 동의 (필수). 빠진 요청도 같은 안내를 하려고 Boolean + @NotNull로 받는다.
        @NotNull(message = "판매자 이용약관에 동의해 주세요.")
        @AssertTrue(message = "판매자 이용약관에 동의해 주세요.")
        Boolean termsAgreed,

        // 개인정보 수집·이용 동의 (필수)
        @NotNull(message = "개인정보 수집·이용에 동의해 주세요.")
        @AssertTrue(message = "개인정보 수집·이용에 동의해 주세요.")
        Boolean privacyAgreed,

        // 화면에 보여 준 약관 버전. 현재 버전(SellerTerms.CURRENT_VERSION)과 다르면 SELLER_009
        @NotBlank(message = "약관 버전이 없습니다. 화면을 새로 고친 뒤 다시 동의해 주세요.")
        @Size(max = SellerTerms.VERSION_MAX_LENGTH)
        String termsVersion
) {
    /** 고객 문의 연락처: 휴대폰·일반 전화(02 등) 모두, 하이픈 없는 숫자 (주문 배송지 연락처와 같은 규칙) */
    public static final String CONTACT_PHONE_PATTERN = "^0\\d{8,10}$";
}
