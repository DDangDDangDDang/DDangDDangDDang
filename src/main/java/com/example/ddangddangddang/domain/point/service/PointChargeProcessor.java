package com.example.ddangddangddang.domain.point.service;

import com.example.ddangddangddang.domain.point.dto.PointChargeCompleteResponse;
import com.example.ddangddangddang.domain.point.entity.PointCharge;
import com.example.ddangddangddang.domain.point.entity.PointHistory;
import com.example.ddangddangddang.domain.point.entity.PointHistoryType;
import com.example.ddangddangddang.domain.point.entity.PointReferenceType;
import com.example.ddangddangddang.domain.point.entity.PointWallet;
import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.domain.point.infra.PaymentInfo;
import com.example.ddangddangddang.domain.point.repository.PointChargeRepository;
import com.example.ddangddangddang.domain.point.repository.PointHistoryRepository;
import com.example.ddangddangddang.domain.point.repository.PointWalletRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * 포트원에서 조회한 결제 결과를 충전 요청에 반영하는 트랜잭션 단계. (PointChargeService가 포트원 조회 뒤에 부른다)
 *
 * 포트원 호출은 이 트랜잭션 밖에서 끝낸다. 락을 잡은 채 외부 API를 기다리면
 * 응답이 늦을 때 DB 커넥션과 락이 그동안 묶여 커넥션 풀이 바닥난다.
 * 대신 여기서 충전 요청 행에 락을 걸고 PENDING인지 다시 확인해, 두 경로가 동시에 들어와도 포인트는 한 번만 지급된다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PointChargeProcessor {

    private final PointChargeRepository pointChargeRepository;
    private final PointWalletRepository pointWalletRepository;
    private final PointHistoryRepository pointHistoryRepository;

    /**
     * 결제 결과를 반영한다. 금액이 다른 결제는 호출하는 쪽이 이미 취소했다고 보고 FAILED로만 바꾼다.
     *
     * @return 반영 후 충전 상태. 충전이 완료됐으면 현재 잔액도 담는다
     */
    @Transactional
    public PointChargeCompleteResponse applyPayment(String paymentId, PaymentInfo payment) {
        PointCharge charge = pointChargeRepository.findByPaymentIdForUpdate(paymentId)
                .orElseThrow(() -> new BusinessException(PointErrorCode.CHARGE_NOT_FOUND));

        // 포트원을 조회하는 사이 다른 경로(프론트·웹훅)가 먼저 처리했으면 결과만 반환한다 (멱등성)
        if (!charge.isPending()) {
            return PointChargeCompleteResponse.of(charge, findBalanceIfCompleted(charge));
        }

        // 아직 결제가 끝나지 않음 (가상계좌 입금 대기 등) → 상태 유지
        if (payment.isInProgress()) {
            return PointChargeCompleteResponse.of(charge, null);
        }

        // 결제 실패/취소, 또는 금액이 달라 취소한 결제
        if (!payment.isPaid() || !charge.matchesAmount(payment.totalAmount())) {
            log.info("결제 미완료로 충전 실패 처리 paymentId={}, status={}", paymentId, payment.status());
            charge.fail();
            return PointChargeCompleteResponse.of(charge, null);
        }

        // 정상 결제 → 포인트 지급 + 원장 기록 + 상태 변경
        return PointChargeCompleteResponse.of(charge, applyCharge(charge).getBalance());
    }

    /**
     * 지갑에 포인트 지급 + 충전 완료 처리 + 이력 저장.
     * 팀 규칙(PointWalletRepository)대로 잔액을 바꿀 때는 지갑에 비관적 락을 건다.
     * 락 순서는 "충전 요청 → 지갑"이고, 입찰 쪽은 지갑만 잠그므로 데드락이 생기지 않는다.
     */
    private PointWallet applyCharge(PointCharge charge) {
        PointWallet wallet = pointWalletRepository.findByUserIdForUpdate(charge.getUserId())
                .orElseThrow(() -> new BusinessException(PointErrorCode.WALLET_NOT_FOUND));

        wallet.charge(charge.getAmount());
        charge.complete();
        pointHistoryRepository.save(PointHistory.of(
                wallet, PointHistoryType.CHARGE, charge.getAmount(),
                PointReferenceType.CHARGE, charge.getId()));

        return wallet;
    }

    /**
     * 완료된 충전이면 지갑의 현재 잔액, 아니면 null
     */
    public Long findBalanceIfCompleted(PointCharge charge) {
        if (!charge.isCompleted()) {
            return null;
        }
        return pointWalletRepository.findByUserId(charge.getUserId())
                .map(PointWallet::getBalance)
                .orElse(null);
    }
}
