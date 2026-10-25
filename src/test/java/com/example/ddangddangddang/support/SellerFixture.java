package com.example.ddangddangddang.support;

import com.example.ddangddangddang.domain.seller.entity.SellerApplication;
import com.example.ddangddangddang.domain.seller.repository.SellerApplicationRepository;

import java.time.LocalDateTime;

/**
 * 통합 테스트용 판매자 인증. 방송을 시작하려면 판매자 인증(관리자 승인)이 있어야 해서,
 * 신청·심사 API를 거치지 않고 승인된 신청을 바로 저장한다.
 */
public final class SellerFixture {

    private SellerFixture() {
    }

    public static void verify(SellerApplicationRepository repository, Long userId) {
        if (repository.findByUserId(userId).filter(SellerApplication::isApproved).isPresent()) {
            return;
        }
        SellerApplication application = SellerApplication.create(userId, "테스트 상점", "테스트 판매자입니다.");
        application.approve(LocalDateTime.now());
        repository.save(application);
    }
}
