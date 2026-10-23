package com.example.ddangddangddang.domain.donation.policy;

import com.example.ddangddangddang.domain.donation.config.DonationProperties;
import com.example.ddangddangddang.global.common.CursorResponse;
import com.example.ddangddangddang.global.config.CacheConfig;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 도네이션 포인트 정책 읽기·바꾸기.
 *
 * - current(): 지금 정책. 선물·적립마다 읽으므로 Redis에 캐시한다. (CacheConfig.DONATION_POLICY, 10분)
 *   DB에 정책이 아직 없으면 설정 기본값(DonationProperties)을 쓴다.
 *   Redis가 잠깐 안 되면 캐시 오류를 로그만 남기고 DB에서 읽는다. (CacheConfig의 오류 처리)
 * - change(): 관리자가 정책을 바꾼다. 새 행을 쌓고(이전 정책은 기록으로 남음), 커밋 뒤 캐시를 지워 모든 서버가 바로 새 정책을 쓴다.
 *
 * 바뀐 정책은 그 뒤의 적립·선물부터 적용된다. 이미 적립·선물한 포인트(행마다 실제 금액·유효기간을 저장)는 바뀌지 않는다.
 * 한 달 한도를 낮추면, 이번 달에 이미 그만큼 쓴 회원은 이번 달에는 더 할 수 없다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DonationPolicies {

    static final int MAX_HISTORY_SIZE = 50;

    private final DonationPolicyVersionRepository versionRepository;
    private final DonationProperties defaults;

    @Cacheable(cacheNames = CacheConfig.DONATION_POLICY, key = "'current'")
    public DonationPolicy current() {
        return versionRepository.findTopByOrderByIdDesc()
                .map(DonationPolicyVersion::toPolicy)
                .orElseGet(defaults::toPolicy);
    }

    /** 관리자 화면: 지금 정책과 기본값을 쓰는 중인지 */
    public DonationPolicyResponse currentForAdmin() {
        return versionRepository.findTopByOrderByIdDesc()
                .map(DonationPolicyResponse::from)
                .orElseGet(() -> DonationPolicyResponse.defaults(defaults.toPolicy()));
    }

    public CursorResponse<DonationPolicyResponse> history(Long cursor, int size) {
        int pageSize = Math.max(1, Math.min(size, MAX_HISTORY_SIZE));
        List<DonationPolicyVersion> fetched = versionRepository.findByCursor(cursor, PageRequest.of(0, pageSize + 1));
        return CursorResponse.of(fetched, pageSize, DonationPolicyResponse::from, DonationPolicyVersion::getId);
    }

    @Transactional
    // 지금 정책 하나만 캐시하므로 그 키를 지운다. (allEntries로 비우면 Redis 캐시가 지워지지 않아 키를 지정한다)
    @CacheEvict(cacheNames = CacheConfig.DONATION_POLICY, key = "'current'")
    public DonationPolicyResponse change(Long adminId, DonationPolicyRequest request) {
        DonationPolicy policy = request.toPolicy();
        DonationPolicyValidator.validate(policy);
        String memo = request.memo() == null || request.memo().isBlank() ? null : request.memo().strip();
        return DonationPolicyResponse.from(versionRepository.save(DonationPolicyVersion.of(policy, adminId, memo)));
    }
}
