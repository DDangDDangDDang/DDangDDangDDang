package com.example.ddangddangddang.domain.point.service;

import com.example.ddangddangddang.domain.point.dto.PointChargeCompleteResponse;
import com.example.ddangddangddang.domain.point.dto.PointChargeRequest;
import com.example.ddangddangddang.domain.point.dto.PointChargeResponse;
import com.example.ddangddangddang.domain.point.entity.PointCharge;
import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.domain.point.infra.PaymentGateway;
import com.example.ddangddangddang.domain.point.infra.PaymentInfo;
import com.example.ddangddangddang.domain.point.repository.PointChargeRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.exception.UserErrorCode;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
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
 * 두 경로 모두 같은 syncWithPayment를 거친다. 포트원 조회·취소는 트랜잭션 밖에서 하고,
 * 결과 반영만 PointChargeProcessor가 충전 요청 행에 락을 걸고 처리해 동시에 들어와도 포인트가 한 번만 지급된다.
 * 그래서 completeCharge·syncChargeFromWebhook에는 트랜잭션을 걸지 않는다.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PointChargeService {

    private final PointChargeRepository pointChargeRepository;
    private final PaymentGateway paymentGateway;
    private final UserRepository userRepository;
    private final PointChargeProcessor pointChargeProcessor;

    @Transactional
    public PointChargeResponse createChargeRequest(Long userId, PointChargeRequest request) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(UserErrorCode.USER_NOT_FOUND));
        PointCharge pointCharge = PointCharge.create(userId, request.getAmount(), generatePaymentId());
        return PointChargeResponse.of(pointChargeRepository.save(pointCharge), user);
    }

    /**
     * 프론트 경로. 본인 확인 후 결제 결과를 반영한다.
     * 실패 시 예외 대신 FAILED 상태를 반환한다.
     */
    public PointChargeCompleteResponse completeCharge(Long userId, String paymentId) {
        PointCharge charge = pointChargeRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new BusinessException(PointErrorCode.CHARGE_NOT_FOUND));
        charge.validateOwner(userId);

        return syncWithPayment(charge);
    }

    /**
     * 웹훅 경로. 로그인 사용자가 없으므로 본인 확인은 하지 않는다.
     * 대신 웹훅 서명 검증(컨트롤러)과 포트원 재조회(syncWithPayment)로 신뢰성을 확보한다.
     *
     * @return 우리 서비스의 충전 요청이 아니면 false (다른 용도의 결제 웹훅일 수 있으므로 무시)
     */
    public boolean syncChargeFromWebhook(String paymentId) {
        Optional<PointCharge> found = pointChargeRepository.findByPaymentId(paymentId);
        if (found.isEmpty()) {
            log.info("충전 요청이 없는 결제 웹훅, 무시 paymentId={}", paymentId);
            return false;
        }

        PointCharge charge = found.get();
        if (charge.isCompleted()) {
            // 충전이 끝난 뒤 온 웹훅: 결제가 취소(환불)됐을 수 있으므로 다시 조회해 취소분을 회수한다.
            syncCancellation(charge);
            return true;
        }
        if (!charge.isPending()) {
            log.info("이미 처리된 충전 요청의 웹훅 paymentId={}, status={}", paymentId, charge.getStatus());
            return true;
        }

        syncWithPayment(charge);
        return true;
    }

    /**
     * 결제 확인 보정(PointChargeRecoveryScheduler). 프론트 호출과 웹훅이 모두 오지 않아 PENDING으로 남은 충전을
     * 포트원에 다시 조회해 반영한다. 아직 결제창에서 결제하지 않았으면(포트원에 결제 없음) 그대로 둔다.
     */
    public void recoverPending(String paymentId) {
        PointCharge charge = pointChargeRepository.findByPaymentId(paymentId)
                .orElseThrow(() -> new BusinessException(PointErrorCode.CHARGE_NOT_FOUND));
        if (!charge.isPending()) {
            return;
        }
        try {
            syncWithPayment(charge);
        } catch (BusinessException e) {
            if (e.getErrorCode() != PointErrorCode.PAYMENT_NOT_FOUND) {
                throw e;
            }
            // 결제창을 열지 않았거나 결제 전에 닫음. 다음 보정에서 다시 본다.
        }
    }

    /**
     * 완료된 충전의 결제가 취소(환불)됐으면 새로 취소된 만큼 포인트를 회수한다.
     * 충전한 포인트는 환전할 수 없고 결제 취소로만 돌려받으므로, 취소된 결제의 포인트가 남아 있으면 안 된다.
     */
    private void syncCancellation(PointCharge charge) {
        PaymentInfo payment = paymentGateway.getPayment(charge.getPaymentId());
        if (charge.newlyCancelled(payment.cancelledAmount()) <= 0) {
            log.info("취소분 없는 충전 완료 웹훅 paymentId={}, status={}", charge.getPaymentId(), payment.status());
            return;
        }
        pointChargeProcessor.applyCancellation(charge.getPaymentId(), payment.cancelledAmount());
    }

    /**
     * 포트원에 결제를 직접 조회해서 충전 요청 상태를 맞춘다. 프론트나 웹훅 본문이 보낸 값은 믿지 않는다.
     * charge는 락 없이 읽은 값이라, 상태 확인은 여기서 한 번(포트원 호출을 줄이려고), 반영할 때 락을 걸고 한 번 더 한다.
     */
    private PointChargeCompleteResponse syncWithPayment(PointCharge charge) {
        // 이미 처리된 요청이면 결과만 반환 (멱등성)
        if (!charge.isPending()) {
            return PointChargeCompleteResponse.of(charge, pointChargeProcessor.findBalanceIfCompleted(charge));
        }

        String paymentId = charge.getPaymentId();
        PaymentInfo payment = paymentGateway.getPayment(paymentId);

        // 결제는 됐는데 금액이 다름 → 위변조 의심, 결제부터 취소한다. (부분 취소된 결제면 남은 금액을 취소한다)
        // 취소가 실패하면 예외가 나서 PENDING으로 남고, 다음 조회 때 다시 시도한다. (먼저 FAILED로 바꾸면 돈만 빠져나간 채 남는다)
        if ((payment.isPaid() || payment.isPartiallyCancelled()) && !charge.matchesAmount(payment.totalAmount())) {
            log.warn("결제 금액 불일치 paymentId={}, status={}, expected={}, actual={}",
                    paymentId, payment.status(), charge.getAmount(), payment.totalAmount());
            paymentGateway.cancelPayment(paymentId, "결제 금액 불일치");
        }

        return pointChargeProcessor.applyPayment(paymentId, payment);
    }

    /**
     * 결제 ID(PG사에 넘어가는 주문번호).
     * KG이니시스는 주문번호를 40자까지만 받는다. "charge-"(7자) + 하이픈 뺀 UUID(32자) = 39자.
     */
    private String generatePaymentId() {
        return "charge-" + UUID.randomUUID().toString().replace("-", "");
    }
}
