package com.example.ddangddangddang.domain.seller.dto;

import com.example.ddangddangddang.domain.seller.entity.SellerApplication;
import com.example.ddangddangddang.domain.seller.entity.SellerApplicationStatus;
import com.example.ddangddangddang.domain.seller.entity.SellerIdentity;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * 판매자 신청. 관리자 목록에서는 신청한 회원의 닉네임·이메일과 본인인증 실명·생년월일도 함께 보여 준다. (내 신청 조회에서는 null)
 * rejectReason: 반려됐을 때만, reviewedAt: 승인·반려한 시각
 * businessNumber: 사업자 판매자의 사업자등록번호(숫자 10자리), 개인 판매자는 null. 신청한 본인과 관리자만 본다.
 * bankCode·accountNumber·accountHolder: 정산 계좌 (신청한 본인과 관리자만). returnAddress·contactPhone: 반품·교환 주소와 고객 문의 연락처 (인증되면 프로필에 공개)
 *   정산 계좌·반품 주소를 받기 전에 낸 신청은 null
 * termsVersion·termsAgreedAt: 신청할 때 동의한 판매자 약관 버전과 동의 시각. 약관 동의를 받기 전에 낸 신청은 null
 * realName·birthDate: 본인인증 결과 (관리자 목록에서만). 본인인증을 받기 전에 낸 신청은 null
 */
public record SellerApplicationResponse(
        Long id,
        Long userId,
        String nickname,
        String email,
        String realName,
        LocalDate birthDate,
        String storeName,
        String introduction,
        String businessNumber,
        String bankCode,
        String accountNumber,
        String accountHolder,
        String returnAddress,
        String contactPhone,
        SellerApplicationStatus status,
        String rejectReason,
        String termsVersion,
        LocalDateTime termsAgreedAt,
        LocalDateTime createdAt,
        LocalDateTime reviewedAt
) {
    public static SellerApplicationResponse from(SellerApplication application) {
        return of(application, null, null, null);
    }

    public static SellerApplicationResponse of(SellerApplication application, String nickname, String email, SellerIdentity identity) {
        return new SellerApplicationResponse(application.getId(), application.getUserId(), nickname, email,
                identity == null ? null : identity.getRealName(), identity == null ? null : identity.getBirthDate(),
                application.getStoreName(), application.getIntroduction(), application.getBusinessNumber(),
                application.getBankCode(), application.getAccountNumber(), application.getAccountHolder(),
                application.getReturnAddress(), application.getContactPhone(), application.getStatus(),
                application.getRejectReason(), application.getTermsVersion(), application.getTermsAgreedAt(),
                application.getCreatedAt(), application.getReviewedAt());
    }
}
