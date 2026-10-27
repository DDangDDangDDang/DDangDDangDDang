package com.example.ddangddangddang.domain.donation.policy;

import java.time.LocalDateTime;

/**
 * 관리자: 도네이션 포인트 정책 한 버전.
 * - id·changedBy·memo·changedAt: 바꾼 기록. 아직 한 번도 바꾸지 않아 설정 기본값을 쓰는 중이면 id·changedBy·changedAt이 null이고 usingDefaults가 true
 */
public record DonationPolicyResponse(
        Long id,
        boolean usingDefaults,
        DonationPolicy policy,
        Long changedBy,
        String memo,
        LocalDateTime changedAt
) {
    static DonationPolicyResponse from(DonationPolicyVersion version) {
        return new DonationPolicyResponse(version.getId(), false, version.toPolicy(), version.getChangedBy(), version.getMemo(),
                version.getCreatedAt());
    }

    static DonationPolicyResponse defaults(DonationPolicy policy) {
        return new DonationPolicyResponse(null, true, policy, null, null, null);
    }
}
