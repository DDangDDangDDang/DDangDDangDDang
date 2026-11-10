package com.example.ddangddangddang.domain.hammer.service;

import com.example.ddangddangddang.domain.hammer.dto.AdminSanctionResponse;
import com.example.ddangddangddang.domain.hammer.dto.SanctionResponse;
import com.example.ddangddangddang.domain.hammer.entity.SanctionRelease;
import com.example.ddangddangddang.domain.hammer.entity.SanctionType;
import com.example.ddangddangddang.domain.hammer.exception.SanctionErrorCode;
import com.example.ddangddangddang.domain.hammer.repository.HammerScoreRepository;
import com.example.ddangddangddang.domain.hammer.repository.SanctionReleaseRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 본인 책임 취소가 반복된 회원의 입찰·경매 등록 정지. 정지를 따로 저장하지 않고 취소 기록으로 그때그때 판단한다.
 * (규칙은 SanctionRule) 관리자가 풀면 푼 시각까지의 취소는 다시 세지 않는다.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SanctionService {

    private static final DateTimeFormatter UNTIL_FORMAT = DateTimeFormatter.ofPattern("M월 d일 HH:mm");

    private final HammerScoreRepository hammerScoreRepository;
    private final SanctionReleaseRepository sanctionReleaseRepository;
    private final UserRepository userRepository;

    /** 시행일. 이 전의 취소는 세지 않는다. (규칙을 알리기 전 취소로 정지하지 않기 위해) */
    @Value("${sanction.counted-from:2026-11-04T00:00:00}")
    private LocalDateTime countedFrom;

    /**
     * 정지 중이면 풀리는 시각을 담아 막는다. 부르는 쪽 트랜잭션 안에서 읽는다.
     * 경매 생성처럼 "잠금 조회가 트랜잭션의 첫 조회"여야 하는 곳에서는 잠금 조회 뒤에 불러야 한다.
     * (먼저 부르면 MySQL 스냅샷 시점이 앞당겨진다. 트랜잭션을 멈추고 따로 읽으면 요청마다 DB 연결을 두 개 써서 몰릴 때 연결 풀이 바닥난다)
     */
    public void requireNotBanned(Long userId, SanctionType type) {
        SanctionRule.Result result = evaluate(userId, type);
        if (result != null && result.isActive(LocalDateTime.now())) {
            SanctionErrorCode code = type == SanctionType.BID_BAN ? SanctionErrorCode.BID_BANNED : SanctionErrorCode.LISTING_BANNED;
            throw new BusinessException(code,
                    code.getMessage() + " (" + result.bannedUntil().format(UNTIL_FORMAT) + "에 풀립니다)");
        }
    }

    /** 지금 걸려 있는 정지 */
    public List<SanctionResponse> mySanctions(Long userId) {
        LocalDateTime now = LocalDateTime.now();
        List<SanctionResponse> sanctions = new ArrayList<>();
        for (SanctionType type : SanctionType.values()) {
            SanctionRule.Result result = evaluate(userId, type);
            if (result != null && result.isActive(now)) {
                sanctions.add(new SanctionResponse(type, result.bannedUntil(), result.cancelCount()));
            }
        }
        return sanctions;
    }

    /** 관리자 검토 대상: 최근 90일 본인 책임 취소가 4회 이상 (관리자가 푼 뒤의 취소만 센다) */
    public List<AdminSanctionResponse> reviewTargets() {
        LocalDateTime since = notBefore(LocalDateTime.now().minusDays(SanctionRule.WINDOW_DAYS));
        List<Candidate> candidates = new ArrayList<>();
        for (Long userId : hammerScoreRepository.findRepeatedBuyerCancelers(
                SanctionType.BID_BAN.cause(), since, SanctionRule.REVIEW_COUNT)) {
            candidates.add(new Candidate(userId, SanctionType.BID_BAN));
        }
        for (Long userId : hammerScoreRepository.findRepeatedSellerCancelers(
                SanctionType.LISTING_BAN.cause(), since, SanctionRule.REVIEW_COUNT)) {
            candidates.add(new Candidate(userId, SanctionType.LISTING_BAN));
        }
        Map<Long, User> users = userRepository.findAllById(candidates.stream().map(Candidate::userId).distinct().toList())
                .stream().collect(Collectors.toMap(User::getId, Function.identity()));

        List<AdminSanctionResponse> targets = new ArrayList<>();
        for (Candidate candidate : candidates) {
            SanctionRule.Result result = evaluate(candidate.userId(), candidate.type());
            if (result != null && result.needsReview()) {
                User user = users.get(candidate.userId());
                targets.add(new AdminSanctionResponse(candidate.userId(), user == null ? null : user.getNickname(),
                        candidate.type(), result.cancelCount(), result.bannedUntil()));
            }
        }
        targets.sort(Comparator.comparing(AdminSanctionResponse::bannedUntil).reversed());
        return targets;
    }

    /** 관리자가 정지를 푼다. 지금까지의 취소는 그 정지 종류에서 다시 세지 않는다 */
    @Transactional
    public void release(Long userId, SanctionType type) {
        if (!userRepository.existsById(userId)) {
            throw new BusinessException(UserErrorCode.USER_NOT_FOUND);
        }
        sanctionReleaseRepository.save(SanctionRelease.of(userId, type, LocalDateTime.now()));
    }

    private SanctionRule.Result evaluate(Long userId, SanctionType type) {
        LocalDateTime since = countingStart();
        List<LocalDateTime> cancelTimes = type == SanctionType.BID_BAN
                ? hammerScoreRepository.findBuyerCancelTimes(userId, type.cause(), since)
                : hammerScoreRepository.findSellerCancelTimes(userId, type.cause(), since);
        LocalDateTime releasedAt = sanctionReleaseRepository.findTopByUserIdAndTypeOrderByReleasedAtDesc(userId, type)
                .map(SanctionRelease::getReleasedAt).orElse(null);
        List<LocalDateTime> counted = releasedAt == null ? cancelTimes
                : cancelTimes.stream().filter(time -> time.isAfter(releasedAt)).toList();
        return SanctionRule.of(counted);
    }

    /**
     * 지금 판단에 필요한 가장 오래된 취소 시각. 정지는 마지막 취소부터 최대 30일이고 그 취소 기준 90일을 세므로 120일 전부터 읽는다.
     * 시행일보다 앞이면 시행일부터.
     */
    private LocalDateTime countingStart() {
        return notBefore(LocalDateTime.now().minusDays(SanctionRule.WINDOW_DAYS + SanctionRule.LONG_BAN_DAYS));
    }

    private LocalDateTime notBefore(LocalDateTime start) {
        return start.isBefore(countedFrom) ? countedFrom : start;
    }

    private record Candidate(Long userId, SanctionType type) {
    }
}
