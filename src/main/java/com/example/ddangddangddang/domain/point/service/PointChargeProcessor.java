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

        // 결제는 됐다가 포인트를 지급하기 전에 일부가 취소된 결제 (서버 장애 사이 포트원 콘솔에서 부분 취소 등).
        // 남은 금액은 사용자가 실제로 낸 돈이라 실패로 끝내면 돈만 빠져나간 채 묻힌다. (FAILED는 보정 작업도 다시 보지 않는다)
        // 그래서 결제 금액 전체를 지급한 뒤 취소분을 회수해, 남은 금액만큼만 포인트로 남긴다.
        if (payment.isPartiallyCancelled() && charge.matchesAmount(payment.totalAmount())) {
            log.info("부분 취소된 결제를 지급 후 취소분 회수로 처리 paymentId={}, total={}, cancelled={}",
                    paymentId, payment.totalAmount(), payment.cancelledAmount());
            PointWallet wallet = applyCharge(charge);
            reclaimCancelled(charge, wallet, payment.cancelledAmount());
            return PointChargeCompleteResponse.of(charge, wallet.getSpendableBalance());
        }

        // 결제 실패·전액 취소, 또는 금액이 달라 취소한 결제
        if (!payment.isPaid() || !charge.matchesAmount(payment.totalAmount())) {
            log.info("결제가 완료되지 않았거나 금액이 달라 충전 실패 처리 paymentId={}, status={}, total={}, cancelled={}",
                    paymentId, payment.status(), payment.totalAmount(), payment.cancelledAmount());
            charge.fail();
            return PointChargeCompleteResponse.of(charge, null);
        }

        // 정상 결제 → 포인트 지급 + 원장 기록 + 상태 변경
        return PointChargeCompleteResponse.of(charge, applyCharge(charge).getSpendableBalance());
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
     * 충전 완료 뒤 결제가 취소(환불)된 만큼 포인트를 회수한다. 락 순서는 applyPayment와 같이 "충전 요청 → 지갑"이다.
     * 웹훅이 두 번 와도 충전 요청에 회수한 누적 금액을 남기므로 같은 취소분을 두 번 회수하지 않는다.
     * 이미 써 버리거나 입찰에 묶여 모자라면 있는 만큼만 회수하고, 남은 금액을 충전 요청(unreclaimedAmount)과
     * 지갑(reclaimDebt)에 남긴다. 그만큼은 쓸 수 없고, 포인트가 생기면 collectUnreclaimed가 마저 회수한다.
     *
     * @param paymentCancelledTotal 포트원이 알려 준 이 결제의 취소 합계
     */
    @Transactional
    public void applyCancellation(String paymentId, long paymentCancelledTotal) {
        PointCharge charge = pointChargeRepository.findByPaymentIdForUpdate(paymentId)
                .orElseThrow(() -> new BusinessException(PointErrorCode.CHARGE_NOT_FOUND));
        if (charge.newlyCancelled(paymentCancelledTotal) <= 0) {
            return;
        }

        PointWallet wallet = pointWalletRepository.findByUserIdForUpdate(charge.getUserId())
                .orElseThrow(() -> new BusinessException(PointErrorCode.WALLET_NOT_FOUND));
        reclaimCancelled(charge, wallet, paymentCancelledTotal);
    }

    /**
     * 완료된 충전의 결제 취소분 중 아직 회수하지 않은 만큼을 지갑에서 회수한다. 충전 요청과 지갑은 이미 잠근 상태로 부른다.
     * (충전 완료 뒤 취소된 결제, 그리고 지급 전에 부분 취소된 결제가 함께 쓴다)
     */
    private void reclaimCancelled(PointCharge charge, PointWallet wallet, long paymentCancelledTotal) {
        long cancelled = charge.newlyCancelled(paymentCancelledTotal);
        if (cancelled <= 0) {
            return;
        }
        String paymentId = charge.getPaymentId();
        long reclaimed = wallet.reclaimCharge(cancelled);
        charge.recordCancellation(cancelled);
        charge.recordShortfall(cancelled - reclaimed);
        if (reclaimed > 0) {
            pointHistoryRepository.save(PointHistory.of(
                    wallet, PointHistoryType.CHARGE_CANCEL, reclaimed, PointReferenceType.CHARGE, charge.getId()));
        }
        if (reclaimed < cancelled) {
            log.warn("결제 취소분을 모두 회수하지 못해 남겨 둠 paymentId={}, userId={}, cancelled={}, reclaimed={}, shortfall={}",
                    paymentId, charge.getUserId(), cancelled, reclaimed, cancelled - reclaimed);
        } else {
            log.info("결제 취소로 충전 포인트 회수 paymentId={}, userId={}, reclaimed={}", paymentId, charge.getUserId(), reclaimed);
        }
    }

    /**
     * 결제 취소 때 다 회수하지 못하고 남겨 둔 포인트를 사용 가능 포인트가 생긴 만큼 마저 회수한다.
     * 락 순서는 applyCancellation과 같이 "충전 요청 → 지갑"이다.
     *
     * @return 이번에 회수한 포인트
     */
    @Transactional
    public long collectUnreclaimed(String paymentId) {
        PointCharge charge = pointChargeRepository.findByPaymentIdForUpdate(paymentId)
                .orElseThrow(() -> new BusinessException(PointErrorCode.CHARGE_NOT_FOUND));
        if (charge.getUnreclaimedAmount() <= 0) {
            return 0;
        }
        PointWallet wallet = pointWalletRepository.findByUserIdForUpdate(charge.getUserId())
                .orElseThrow(() -> new BusinessException(PointErrorCode.WALLET_NOT_FOUND));
        long collected = wallet.collectReclaimDebt(charge.getUnreclaimedAmount());
        if (collected <= 0) {
            return 0;
        }
        charge.recordCollected(collected);
        pointHistoryRepository.save(PointHistory.of(
                wallet, PointHistoryType.CHARGE_CANCEL, collected, PointReferenceType.CHARGE, charge.getId()));
        log.info("남겨 둔 결제 취소분 회수 paymentId={}, userId={}, collected={}, remaining={}",
                paymentId, charge.getUserId(), collected, charge.getUnreclaimedAmount());
        return collected;
    }

    /**
     * 완료된 충전이면 지갑의 현재 잔액, 아니면 null
     */
    public Long findBalanceIfCompleted(PointCharge charge) {
        if (!charge.isCompleted()) {
            return null;
        }
        return pointWalletRepository.findByUserId(charge.getUserId())
                .map(PointWallet::getSpendableBalance)
                .orElse(null);
    }
}
