package com.example.ddangddangddang.domain.point.infra;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

/**
 * 포트원 웹훅 본문 중 필요한 값만 받는다.
 * 예: {"type":"Transaction.Paid","timestamp":"...","data":{"paymentId":"...","storeId":"...","transactionId":"..."}}
 *
 * 웹훅 본문의 상태는 믿지 않는다. paymentId만 꺼내서 포트원 API로 다시 조회한다.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record PortOneWebhookPayload(String type, Data data) {

    private static final String TRANSACTION_PREFIX = "Transaction.";

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Data(String paymentId) {
    }

    /** 결제 관련 웹훅이고 paymentId가 있는지 (빌링키 발급 등 다른 웹훅은 무시) */
    public boolean isTransactionEvent() {
        return type != null && type.startsWith(TRANSACTION_PREFIX)
                && data != null && data.paymentId() != null && !data.paymentId().isBlank();
    }

    public String paymentId() {
        return data == null ? null : data.paymentId();
    }
}
