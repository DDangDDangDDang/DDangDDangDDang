package com.example.ddangddangddang.domain.order.event;

import com.example.ddangddangddang.domain.directmessage.service.DirectMessageService;
import com.example.ddangddangddang.domain.order.entity.OrderCancelReason;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

@ExtendWith(MockitoExtension.class)
class OrderDmNotifierTest {

    private static final Long BUYER = 1L;
    private static final Long SELLER = 2L;
    private static final String LABEL = "[경매 #123 · 빈티지 시계] ";

    @InjectMocks
    private OrderDmNotifier notifier;

    @Mock
    private DirectMessageService directMessageService;

    @Test
    @DisplayName("지연 신청 안내는 경매 표시로 시작하고 사유를 담아 판매자가 구매자에게 보낸다")
    void delayRequested_messageStartsWithAuctionLabel() {
        notifier.handle(new OrderDelayRequestedEvent(7L, 123L, BUYER, SELLER, "빈티지 시계", "입고 지연"));

        String content = sent(SELLER, BUYER, "order-delay-request-7");
        assertThat(content).startsWith(LABEL + "판매자가 발송 지연을 신청했어요.");
        assertThat(content).contains("사유: 입고 지연", "승인하면 발송 기한이 7일 늘어납니다.");
    }

    @Test
    @DisplayName("승인 결과는 구매자가 판매자에게 보낸다")
    void delayApproved_buyerSendsResultToSeller() {
        notifier.handle(new OrderDelayRespondedEvent(7L, 123L, BUYER, SELLER, "빈티지 시계", true));

        assertThat(sent(BUYER, SELLER, "order-delay-response-7")).startsWith(LABEL + "구매자가 발송 지연을 승인했어요.");
    }

    @Test
    @DisplayName("거절 결과에는 기존 기한이 유지된다고 적는다")
    void delayRejected_messageSaysDeadlineKept() {
        notifier.handle(new OrderDelayRespondedEvent(7L, 123L, BUYER, SELLER, "빈티지 시계", false));

        assertThat(sent(BUYER, SELLER, "order-delay-response-7"))
                .startsWith(LABEL + "구매자가 발송 지연을 거절했어요.").contains("기존 발송 기한");
    }

    @Test
    @DisplayName("취소 안내는 사유를 구분해 적는다")
    void orderCanceled_messageStatesReason() {
        notifier.handle(new OrderCanceledEvent(7L, 123L, BUYER, SELLER, "빈티지 시계", OrderCancelReason.SELLER_NOT_SHIPPED));

        assertThat(sent(SELLER, BUYER, "order-cancel-7")).startsWith(LABEL).contains("발송되지 않아", "전액 환불");
    }

    @Test
    @DisplayName("메신저 전송에 실패해도 예외를 밖으로 던지지 않는다")
    void sendFailure_isSwallowed() {
        willThrow(new RuntimeException("실패")).given(directMessageService).sendOrderNotice(any(), any(), any(), any());

        assertThatCode(() -> notifier.handle(new OrderCanceledEvent(7L, 123L, BUYER, SELLER, "빈티지 시계",
                OrderCancelReason.BUYER_NO_ADDRESS))).doesNotThrowAnyException();
    }

    private String sent(Long sender, Long receiver, String clientMessageId) {
        ArgumentCaptor<String> content = ArgumentCaptor.forClass(String.class);
        then(directMessageService).should().sendOrderNotice(eq(sender), eq(receiver), eq(clientMessageId), content.capture());
        return content.getValue();
    }
}
