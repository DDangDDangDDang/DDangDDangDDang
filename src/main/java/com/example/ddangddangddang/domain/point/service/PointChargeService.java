package com.example.ddangddangddang.domain.point.service;

import com.example.ddangddangddang.domain.point.dto.PointChargeCompleteResponse;
import com.example.ddangddangddang.domain.point.dto.PointChargeRequest;
import com.example.ddangddangddang.domain.point.dto.PointChargeResponse;
import com.example.ddangddangddang.domain.point.entity.*;
import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.domain.point.infra.PaymentInfo;
import com.example.ddangddangddang.domain.point.infra.PaymentClient;
import com.example.ddangddangddang.domain.point.repository.PointChargeRepository;
import com.example.ddangddangddang.domain.point.repository.PointHistoryRepository;
import com.example.ddangddangddang.domain.point.repository.PointWalletRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
@Transactional
public class PointChargeService {

    private final PointChargeRepository pointChargeRepository;
    private final PointWalletRepository pointWalletRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final PaymentClient paymentClient;

    private String generatePaymentId() {
        return "point-charge-" + UUID.randomUUID();
    }

    @Transactional
    public PointChargeResponse createChargeRequest(Long userId, PointChargeRequest request) {
        PointCharge pointCharge = PointCharge.create(userId, request.getAmount(), generatePaymentId());
        return PointChargeResponse.from(pointChargeRepository.save(pointCharge));
    }

    /**
     * 결제 완료 처리.
     * 실패 시 예외 대신 FAILED 상태를 반환한다. 예외를 던지면 롤백되어 fail()로 바꾼 상태도 사라지기 때문.
     */
    @Transactional
    public PointChargeCompleteResponse completeCharge(Long userId, String paymentId) {
        // 1. 충전 요청 조회 (락) + 본인 확인
        PointCharge charge = pointChargeRepository.findByPaymentIdForUpdate(paymentId)
                .orElseThrow(() -> new BusinessException(PointErrorCode.CHARGE_NOT_FOUND));
        charge.validateOwner(userId);

        // 2. 이미 처리된 요청이면 결과만 반환 (멱등성)
        if (!charge.isPending()) {
            return PointChargeCompleteResponse.of(charge, findBalanceIfCompleted(charge));
        }

        // 3. 포트원에 직접 조회. 프론트가 보낸 값은 믿지 않는다.
        PaymentInfo payment = paymentClient.getPayment(paymentId);

        // 4-1. 아직 결제가 끝나지 않음 (가상계좌 입금 대기 등) → 상태 유지
        if (payment.isInProgress()) {
            return PointChargeCompleteResponse.of(charge, null);
        }

        // 4-2. 결제 실패/취소
        if (!payment.isPaid()) {
            log.info("결제 미완료로 충전 실패 처리 paymentId={}, status={}", paymentId, payment.status());
            charge.fail();
            return PointChargeCompleteResponse.of(charge, null);
        }

        // 4-3. 결제는 됐는데 금액이 다름 → 위변조 의심, 결제 취소 후 실패 처리
        if (!charge.matchesAmount(payment.totalAmount())) {
            log.warn("결제 금액 불일치 paymentId={}, expected={}, actual={}",
                    paymentId, charge.getAmount(), payment.totalAmount());
            paymentClient.cancelPayment(paymentId, "결제 금액 불일치");
            charge.fail();
            return PointChargeCompleteResponse.of(charge, null);
        }

        // 5. 정상 결제 → 포인트 지급 + 원장 기록 + 상태 변경
        PointWallet wallet = applyCharge(charge);
        return PointChargeCompleteResponse.of(charge, wallet.getBalance());
    }

    /**
     * 지갑에 포인트 지급 + 충전 완료 처리 + 이력 저장.
     * 지갑 동시 수정은 PointWallet의 @Version(낙관적 락)이 막아준다.
     * 충돌 시 트랜잭션 전체가 롤백되어 charge는 PENDING으로 남고, 다시 complete를 호출하면 된다.
     */
    private PointWallet applyCharge(PointCharge charge) {
        PointWallet wallet = pointWalletRepository.findByUserId(charge.getUserId())
                .orElseThrow(() -> new BusinessException(PointErrorCode.WALLET_NOT_FOUND));

        wallet.addBalance(charge.getAmount());
        charge.complete();
        pointHistoryRepository.save(PointHistory.of(
                wallet, PointHistoryType.CHARGE, charge.getAmount(),
                PointReferenceType.CHARGE, charge.getId()));

        return wallet;
    }

    private Long findBalanceIfCompleted(PointCharge charge) {
        if (!charge.isCompleted()) {
            return null;
        }
        return pointWalletRepository.findByUserId(charge.getUserId())
                .map(PointWallet::getBalance)
                .orElse(null);
    }

}
