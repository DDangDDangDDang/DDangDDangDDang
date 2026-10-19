package com.example.ddangddangddang.domain.point.entity;

import com.example.ddangddangddang.domain.point.exception.PointErrorCode;
import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.global.exception.CommonErrorCode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 포인트 → 현금 환전 신청. 1포인트 = 1원, 수수료 없음.
 */
@Getter
@Entity
@Table(
        name = "point_exchanges",
        indexes = {
                @Index(name = "idx_point_exchanges_user_id", columnList = "user_id, id"),
                @Index(name = "idx_point_exchanges_status", columnList = "status, id")
        }
)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PointExchange extends BaseTimeEntity {

    public static final long MIN_AMOUNT = 1_000L;
    public static final long MAX_AMOUNT = 1_000_000L;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private Long userId;

    @Column(nullable = false)
    private Long amount;

    /** 송금사에 보내는 멱등 키. 같은 값으로 여러 번 요청해도 송금은 한 번만 일어나야 한다 */
    @Column(nullable = false, unique = true, length = 64)
    private String payoutId;

    @Column(nullable = false, length = 3)
    private String bankCode;

    /** 숫자만 저장한다. 실서비스에서는 암호화 저장이 필요하다 */
    @Column(nullable = false, length = 20)
    private String accountNumber;

    @Column(nullable = false, length = 30)
    private String accountHolder;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private PointExchangeType status;

    private String failReason;

    /** 송금 시작 시각. PROCESSING에 오래 머문 건을 복구 스케줄러가 찾을 때 쓴다 */
    private LocalDateTime processingStartedAt;

    private PointExchange(Long userId, Long amount, String payoutId,
                          String bankCode, String accountNumber, String accountHolder) {
        this.userId = userId;
        this.amount = amount;
        this.payoutId = payoutId;
        this.bankCode = bankCode;
        this.accountNumber = accountNumber;
        this.accountHolder = accountHolder;
        this.status = PointExchangeType.PENDING;
    }

    public static PointExchange create(Long userId, Long amount, String payoutId,
                                       String bankCode, String accountNumber, String accountHolder) {
        if (userId == null) {
            throw new BusinessException(CommonErrorCode.INVALID_INPUT);
        }
        if (amount == null || amount < MIN_AMOUNT || amount > MAX_AMOUNT) {
            throw new BusinessException(PointErrorCode.EXCHANGE_AMOUNT_OUT_OF_RANGE);
        }
        if (isBlank(payoutId)) {
            throw new BusinessException(PointErrorCode.INVALID_PAYOUT_ID);
        }

        String normalizedAccount = normalizeAccountNumber(accountNumber);
        if (isBlank(bankCode) || isBlank(accountHolder)
                || normalizedAccount.isEmpty() || !normalizedAccount.chars().allMatch(Character::isDigit)) {
            throw new BusinessException(PointErrorCode.INVALID_BANK_ACCOUNT);
        }

        return new PointExchange(userId, amount, payoutId, bankCode, normalizedAccount, accountHolder.strip());
    }

    // ===== 상태 확인 =====

    public boolean isPending() {
        return this.status == PointExchangeType.PENDING;
    }

    public boolean isProcessing() {
        return this.status == PointExchangeType.PROCESSING;
    }

    // ===== 검증 =====

    public void validateOwner(Long userId) {
        if (!this.userId.equals(userId)) {
            throw new BusinessException(PointErrorCode.EXCHANGE_ACCESS_DENIED);
        }
    }

    // ===== 상태 변경 =====

    /** 사용자 취소. 송금 시작 전에만 가능하고, 호출한 쪽에서 포인트를 환불해야 한다 */
    public void cancel() {
        validateStatus(PointExchangeType.PENDING);
        this.status = PointExchangeType.CANCELLED;
    }

    /** 송금 시작. 이후로는 취소할 수 없다 */
    public void startProcessing(LocalDateTime now) {
        validateStatus(PointExchangeType.PENDING);
        this.status = PointExchangeType.PROCESSING;
        this.processingStartedAt = now;
    }

    /** 송금 성공 */
    public void complete() {
        validateStatus(PointExchangeType.PROCESSING);
        this.status = PointExchangeType.COMPLETE;
    }

    /** 송금이 확실히 실패. 호출한 쪽에서 포인트를 환불해야 한다 */
    public void fail(String reason) {
        validateStatus(PointExchangeType.PROCESSING);
        this.status = PointExchangeType.FAILED;
        this.failReason = reason;
    }

    private void validateStatus(PointExchangeType expected) {
        if (this.status != expected) {
            throw new BusinessException(PointErrorCode.INVALID_EXCHANGE_STATUS);
        }
    }

    private static String normalizeAccountNumber(String accountNumber) {
        if (accountNumber == null) {
            return "";
        }
        return accountNumber.replace("-", "").replace(" ", "");
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
