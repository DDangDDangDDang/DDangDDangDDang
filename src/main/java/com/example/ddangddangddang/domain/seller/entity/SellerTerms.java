package com.example.ddangddangddang.domain.seller.entity;

/**
 * 판매자 이용약관·개인정보 수집 동의서의 현재 버전. 판매자 신청은 이 버전에 동의해야 낼 수 있다.
 * 약관 본문은 프론트엔드(frontend/src/lib/seller-terms.ts)에 있다. 본문을 바꾸면 두 곳의 버전을 함께 올린다.
 * 예전 버전 화면에서 낸 신청은 SELLER_009로 막아, 바뀐 약관을 다시 보고 동의하게 한다.
 */
public final class SellerTerms {

    public static final String CURRENT_VERSION = "2026-11-02";
    public static final int VERSION_MAX_LENGTH = 20;

    private SellerTerms() {
    }

    public static boolean isCurrent(String version) {
        return CURRENT_VERSION.equals(version);
    }
}
