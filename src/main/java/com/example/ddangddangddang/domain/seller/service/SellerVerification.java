package com.example.ddangddangddang.domain.seller.service;

import com.example.ddangddangddang.domain.seller.entity.SellerApplicationStatus;
import com.example.ddangddangddang.domain.seller.repository.SellerApplicationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

/**
 * 판매자 인증 여부. 관리자가 판매자 신청을 승인한 회원이 판매자 인증 회원이다.
 * 다른 도메인(방송 카드, 1:1 채팅 등)이 인증 마크를 붙일 때 이 클래스만 쓴다.
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SellerVerification {

    private final SellerApplicationRepository applicationRepository;

    public boolean isVerified(Long userId) {
        return userId != null && applicationRepository.existsByUserIdAndStatus(userId, SellerApplicationStatus.APPROVED);
    }

    /** userIds 중 판매자 인증 회원 */
    public Set<Long> verifiedAmong(Collection<Long> userIds) {
        if (userIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(applicationRepository.findApprovedUserIds(userIds));
    }
}
