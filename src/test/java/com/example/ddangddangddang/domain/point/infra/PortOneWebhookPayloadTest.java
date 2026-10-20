package com.example.ddangddangddang.domain.point.infra;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PortOneWebhookPayloadTest {

    @Test
    @DisplayName("Transaction 이벤트이고 paymentId가 있으면 처리 대상이다")
    void transaction() {
        PortOneWebhookPayload payload = new PortOneWebhookPayload(
                "Transaction.Paid", new PortOneWebhookPayload.Data("point-charge-1"));

        assertThat(payload.isTransactionEvent()).isTrue();
        assertThat(payload.paymentId()).isEqualTo("point-charge-1");
    }

    @Test
    @DisplayName("Transaction이 아닌 이벤트나 paymentId가 없으면 처리 대상이 아니다")
    void notTransaction() {
        assertThat(new PortOneWebhookPayload("BillingKey.Issued", new PortOneWebhookPayload.Data(null))
                .isTransactionEvent()).isFalse();
        assertThat(new PortOneWebhookPayload("Transaction.Paid", null).isTransactionEvent()).isFalse();
        assertThat(new PortOneWebhookPayload(null, new PortOneWebhookPayload.Data("p")).isTransactionEvent()).isFalse();
    }
}
