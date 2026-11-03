package com.example.ddangddangddang.domain.order.event;

import com.example.ddangddangddang.domain.directmessage.service.DirectMessageService;
import com.example.ddangddangddang.domain.order.entity.OrderCancelReason;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 지연 신청·응답과 주문 취소를 구매자-판매자 메신저로 안내한다. 어떤 경매 건인지 메시지 첫머리에 보여 준다.
 *
 * 주문이 커밋된 뒤 다른 스레드에서 처리한다. 안내가 실패해도 주문 처리는 그대로이므로 기록만 남긴다.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OrderDmNotifier {

    private final DirectMessageService directMessageService;

    @Async
    @TransactionalEventListener
    public void handle(OrderDelayRequestedEvent event) {
        send(event.orderId(), event.sellerId(), event.buyerId(), "order-delay-request-" + event.orderId(),
                label(event.auctionId(), event.productName()) + "판매자가 발송 지연을 신청했어요.\n"
                        + "사유: " + event.reason() + "\n"
                        + "승인하면 발송 기한이 7일 늘어납니다. (주문 상세에서 승인/거절)");
    }

    @Async
    @TransactionalEventListener
    public void handle(OrderDelayRespondedEvent event) {
        String result = event.approved()
                ? "구매자가 발송 지연을 승인했어요. 발송 기한이 7일 늘어났습니다."
                : "구매자가 발송 지연을 거절했어요. 기존 발송 기한은 그대로입니다.";
        send(event.orderId(), event.buyerId(), event.sellerId(), "order-delay-response-" + event.orderId(),
                label(event.auctionId(), event.productName()) + result);
    }

    @Async
    @TransactionalEventListener
    public void handle(OrderCanceledEvent event) {
        String reason = event.reason() == OrderCancelReason.BUYER_NO_ADDRESS
                ? "배송지가 기한 안에 입력되지 않아 낙찰이 취소됐어요."
                : "기한 안에 발송되지 않아 판매가 취소됐어요.";
        String settlement = event.penaltyAmount() > 0
                ? " 위약금 " + point(event.penaltyAmount()) + "는 판매자에게 지급되고, " + point(event.refundAmount()) + "는 구매자에게 환불됐습니다."
                : " 결제 포인트는 구매자에게 전액 환불됐습니다.";
        send(event.orderId(), event.sellerId(), event.buyerId(), "order-cancel-" + event.orderId(),
                label(event.auctionId(), event.productName()) + reason + settlement);
    }

    private String point(long amount) {
        return String.format("%,dP", amount);
    }

    private String label(Long auctionId, String productName) {
        return "[경매 #" + auctionId + " · " + productName + "] ";
    }

    private void send(Long orderId, Long senderId, Long receiverId, String clientMessageId, String content) {
        try {
            directMessageService.sendOrderNotice(senderId, receiverId, clientMessageId, content);
        } catch (RuntimeException e) {
            log.warn("주문 안내 메시지를 보내지 못했습니다. orderId={}", orderId, e);
        }
    }
}
