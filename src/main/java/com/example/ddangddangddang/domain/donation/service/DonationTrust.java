package com.example.ddangddangddang.domain.donation.service;

import com.example.ddangddangddang.domain.donation.config.DonationProperties;
import com.example.ddangddangddang.domain.donation.repository.DonationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

/**
 * 판매자 신뢰 지표 중 도네이션 부분. 판매자 프로필이 읽는다.
 * 받은 포인트 합계만 보면 한 사람이 몰아 준 것과 구분이 안 되므로, 응원한 서로 다른 사람 수를 함께 준다.
 * (사람 수는 계정을 여러 개 만들어야 부풀릴 수 있어 가장 믿을 만한 지표다)
 */
@Component
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class DonationTrust {

    private final DonationRepository donationRepository;
    private final DonationProperties properties;

    /** @return 최근 trustWindow(기본 90일) 동안의 응원한 사람 수, 받은 포인트, 기간(일) */
    public Summary of(Long sellerId) {
        LocalDateTime since = LocalDateTime.now().minus(properties.trustWindow());
        return new Summary(
                donationRepository.countDonorsSince(sellerId, since),
                donationRepository.sumReceivedSince(sellerId, since),
                properties.trustWindow().toDays()
        );
    }

    public record Summary(long donorCount, long receivedPoints, long windowDays) {
    }
}
