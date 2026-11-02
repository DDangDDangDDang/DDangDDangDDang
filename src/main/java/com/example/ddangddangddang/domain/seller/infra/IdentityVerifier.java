package com.example.ddangddangddang.domain.seller.infra;

/**
 * 휴대폰 본인인증 결과 조회. 화면에서 본인인증 창(포트원)을 마치면 그 본인인증 ID로 결과를 가져온다.
 * 결과를 화면이 보내 주는 값으로 받지 않고 서버가 직접 조회해야, 이름·생년월일을 꾸며 보낼 수 없다.
 *
 * seller.identity.provider로 구현을 고른다. portone(기본): 포트원 API 조회, fake: 로컬 개발용 가짜 결과
 */
public interface IdentityVerifier {

    /**
     * 인증이 끝난(VERIFIED) 본인인증의 결과. 인증이 안 끝났거나 없는 ID면 SELLER_011,
     * 본인인증 업체와 통신하지 못하면 SELLER_015
     */
    VerifiedIdentity verify(String identityVerificationId);
}
