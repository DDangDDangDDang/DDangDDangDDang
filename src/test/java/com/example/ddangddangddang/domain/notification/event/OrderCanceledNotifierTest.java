package com.example.ddangddangddang.domain.notification.event;

import com.example.ddangddangddang.domain.notification.entity.Notification;
import com.example.ddangddangddang.domain.notification.entity.NotificationContent;
import com.example.ddangddangddang.domain.notification.entity.NotificationType;
import com.example.ddangddangddang.domain.notification.service.NotificationDispatcher;
import com.example.ddangddangddang.domain.order.entity.OrderCancelReason;
import com.example.ddangddangddang.domain.order.event.OrderCanceledEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

@ExtendWith(MockitoExtension.class)
class OrderCanceledNotifierTest {

    @InjectMocks
    private OrderCanceledNotifier notifier;

    @Mock
    private NotificationDispatcher notificationDispatcher;

    @Test
    @DisplayName("구매자와 판매자 모두에게 주문 취소 알림을 보낸다")
    void orderCanceled_dispatchesToBuyerAndSeller() {
        assertThat(dispatched()).extracting(Notification::getUserId).containsExactly(1L, 2L);
    }

    @Test
    @DisplayName("알림 내용은 취소를 알리고 주문 내역으로 연결한다")
    void orderCanceled_contentLinksToOrders() {
        NotificationContent content = NotificationContent.of(dispatched().get(0));

        assertThat(content.title()).isEqualTo("주문이 취소됐어요");
        assertThat(content.body()).isEqualTo("‘아이패드’ 주문이 기한 초과로 취소됐어요. 결제 포인트는 구매자에게 전액 환불돼요.");
        assertThat(content.link()).isEqualTo("/orders");
    }

    @Test
    @DisplayName("위약금이 있으면 금액을 알린다")
    void orderCanceled_withPenalty_statesPenalty() {
        NotificationContent content = NotificationContent.of(dispatched(10_000L).get(0));

        assertThat(content.body()).isEqualTo("‘아이패드’ 주문이 배송지 미입력으로 취소됐어요. 위약금 10,000P는 판매자에게 지급되고 나머지는 구매자에게 환불돼요.");
    }

    private List<Notification> dispatched() {
        return dispatched(0L);
    }

    @SuppressWarnings("unchecked")
    private List<Notification> dispatched(long penalty) {
        notifier.handle(new OrderCanceledEvent(7L, 123L, 1L, 2L, "아이패드", OrderCancelReason.BUYER_NO_ADDRESS, 100_000L - penalty, penalty));
        ArgumentCaptor<List<Notification>> captor = ArgumentCaptor.forClass(List.class);
        then(notificationDispatcher).should().dispatch(captor.capture());
        assertThat(captor.getValue()).extracting(Notification::getType).containsOnly(NotificationType.ORDER_CANCELED);
        return captor.getValue();
    }
}
