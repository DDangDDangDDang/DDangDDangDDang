package com.example.ddangddangddang.domain.point.service;

import com.example.ddangddangddang.domain.point.dto.PointChargeCompleteResponse;
import com.example.ddangddangddang.domain.point.dto.PointChargeRequest;
import com.example.ddangddangddang.domain.point.dto.PointChargeResponse;
import com.example.ddangddangddang.domain.point.entity.PointCharge;
import com.example.ddangddangddang.domain.point.entity.PointHistory;
import com.example.ddangddangddang.domain.point.entity.PointHistoryType;
import com.example.ddangddangddang.domain.point.entity.PointReferenceType;
import com.example.ddangddangddang.domain.point.entity.PointWallet;
import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.domain.point.infra.PaymentGateway;
import com.example.ddangddangddang.domain.point.infra.PaymentInfo;
import com.example.ddangddangddang.domain.point.repository.PointChargeRepository;
import com.example.ddangddangddang.domain.point.repository.PointHistoryRepository;
import com.example.ddangddangddang.domain.point.repository.PointWalletRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;
import java.util.UUID;

/**
 * 포인트 충전. 결제 결과는 두 경로로 들어온다.
 * 1. completeCharge: 결제창이 끝난 뒤 프론트가 호출 (로그인 사용자, 본인 확인)
 * 2. syncChargeFromWebhook: 포트원이 서버로 직접 알려줌 (프론트 호출이 안 와도 처리되게 하는 안전망)
 *
 * 두 경로 모두 같은 syncWithPayment를 거치고, 충전 요청 행에 비관적 락을 걸어
 * 동시에 들어와도 포인트가 한 번만 지급된다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PointChargeService {

    private final PointChargeRepository pointChargeRepository;
    private final PointWalletRepository pointWalletRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final PaymentGateway paymentGateway;

    @Transactional
    public PointChargeResponse createChargeRequest(Long userId, PointChargeRequest request) {
        PointCharge pointCharge = PointCharge.create(userId, request.getAmount(), generatePaymentId());
        return PointChargeResponse.from(pointChargeRepository.save(pointCharge));
    }

    /**
     * 프론트 경로. 본인 확인 후 결제 결과를 반영한다.
     * 실패 시 예외 대신 FAILED 상태를 반환한다. 예외를 던지면 롤백되어 fail()로 바꾼 상태도 사라지기 때문.
     */
    @Transactional
    public PointChargeCompleteResponse completeCharge(Long userId, String paymentId) {
        PointCharge charge = pointChargeRepository.findByPaymentIdForUpdate(paymentId)
                .orElseThrow(() -> new BusinessException(PointErrorCode.CHARGE_NOT_FOUND));
        charge.validateOwner(userId);

        Long balance = syncWithPayment(charge);
        return PointChargeCompleteResponse.of(charge, balance);
    }

    /**
     * 웹훅 경로. 로그인 사용자가 없으므로 본인 확인은 하지 않는다.
     * 대신 웹훅 서명 검증(컨트롤러)과 포트원 재조회(syncWithPayment)로 신뢰성을 확보한다.
     *
     * @return 우리 서비스의 충전 요청이 아니면 false (다른 용도의 결제 웹훅일 수 있으므로 무시)
     */
    @Transactional
    public boolean syncChargeFromWebhook(String paymentId) {
        Optional<PointCharge> found = pointChargeRepository.findByPaymentIdForUpdate(paymentId);
        if (found.isEmpty()) {
            log.info("충전 요청이 없는 결제 웹훅, 무시 paymentId={}", paymentId);
            return false;
        }

        PointCharge charge = found.get();
        if (!charge.isPending()) {
            // 이미 complete 경로에서 처리됨. 단, 완료 후 결제가 취소된 경우는 아직 처리하지 않는다 (TODO: 포인트 회수)
            log.info("이미 처리된 충전 요청의 웹훅 paymentId={}, status={}", paymentId, charge.getStatus());
            return true;
        }

        syncWithPayment(charge);
        return true;
    }

    /**
     * 포트원에 결제를 직접 조회해서 충전 요청 상태를 맞춘다. 프론트나 웹훅 본문이 보낸 값은 믿지 않는다.
     * 호출 전 반드시 충전 요청 행에 락이 걸려 있어야 한다.
     *
     * @return 충전이 완료됐으면 현재 잔액, 아니면 null
     */
    private Long syncWithPayment(PointCharge charge) {
        // 이미 처리된 요청이면 결과만 반환 (멱등성)
        if (!charge.isPending()) {
            return findBalanceIfCompleted(charge);
        }

        String paymentId = charge.getPaymentId();
        PaymentInfo payment = paymentGateway.getPayment(paymentId);

        // 아직 결제가 끝나지 않음 (가상계좌 입금 대기 등) → 상태 유지
        if (payment.isInProgress()) {
            return null;
        }

        // 결제 실패/취소
        if (!payment.isPaid()) {
            log.info("결제 미완료로 충전 실패 처리 paymentId={}, status={}", paymentId, payment.status());
            charge.fail();
            return null;
        }

        // 결제는 됐는데 금액이 다름 → 위변조 의심, 결제 취소 후 실패 처리
        if (!charge.matchesAmount(payment.totalAmount())) {
            log.warn("결제 금액 불일치 paymentId={}, expected={}, actual={}",
                    paymentId, charge.getAmount(), payment.totalAmount());
            paymentGateway.cancelPayment(paymentId, "결제 금액 불일치");
            charge.fail();
            return null;
        }

        // 정상 결제 → 포인트 지급 + 원장 기록 + 상태 변경
        return applyCharge(charge).getBalance();
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

    private Long findBalanceIfCompleted(PointCharge charge) {
        if (!charge.isCompleted()) {
            return null;
        }
        return pointWalletRepository.findByUserId(charge.getUserId())
                .map(PointWallet::getBalance)
                .orElse(null);
    }

    private String generatePaymentId() {
        return "point-charge-" + UUID.randomUUID();
    }
}
