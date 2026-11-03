package com.example.ddangddangddang.domain.seller.event;

/**
 * 관리자가 판매자 신청을 승인했다. 승인 트랜잭션이 커밋된 뒤 처리한다. (판매자 승인 축하 마일리지: SellerWelcomeRewarder)
 */
public record SellerApprovedEvent(Long applicationId, Long userId) {
}
