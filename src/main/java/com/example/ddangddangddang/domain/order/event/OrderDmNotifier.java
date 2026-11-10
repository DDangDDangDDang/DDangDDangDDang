package com.example.ddangddangddang.domain.order.event;

import com.example.ddangddangddang.domain.directmessage.service.DirectMessageService;
import com.example.ddangddangddang.domain.order.entity.OrderCancelReason;
import com.example.ddangddangddang.domain.order.entity.OrderIssueType;
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
        // 책임 있는 쪽에 반복되면 받을 정지를 알린다 (규칙: hammer 도메인의 SanctionRule)
        String warning = event.reason() == OrderCancelReason.BUYER_NO_ADDRESS
                ? " 배송지 미입력 낙찰취소가 90일 안에 2회가 되면 입찰이 7일 정지됩니다."
                : " 미발송 판매 취소가 90일 안에 2회가 되면 경매 등록이 7일 정지됩니다.";
        send(event.orderId(), event.sellerId(), event.buyerId(), "order-cancel-" + event.orderId(),
                label(event.auctionId(), event.productName()) + reason + settlement + warning);
    }

    /** 자동 구매 확정이 멈춰 관리자가 확인한다고 판매자에게 알린다. (구매자 신고면 구매자 쪽에서, 송장 확인 실패면 판매자가 받는다) */
    @Async
    @TransactionalEventListener
    public void handle(OrderReviewRequestedEvent event) {
        String reason = switch (event.reason()) {
            case BUYER_REPORTED -> "구매자가 문제를 신고했어요. (" + issueLabel(event.issueType()) + ")";
            case TRACKING_NOT_FOUND -> "발송 후 7일이 지났는데 택배사에서 송장이 조회되지 않아요. 송장번호를 확인해 주세요.";
            case TRACKING_NOT_DELIVERED -> "발송 후 14일이 지났는데 배송 완료가 확인되지 않아요.";
            case TRACKING_UNAVAILABLE -> "배송 조회가 되지 않아 배송 완료를 확인하지 못했어요.";
        };
        send(event.orderId(), event.buyerId(), event.sellerId(), "order-review-" + event.orderId() + "-" + event.reason(),
                label(event.auctionId(), event.productName()) + reason
                        + "\n자동 구매 확정을 멈추고 관리자가 확인한 뒤 정산 또는 환불로 처리합니다.");
    }

    @Async
    @TransactionalEventListener
    public void handle(OrderReviewResolvedEvent event) {
        String result = event.refunded()
                ? "관리자 확인 결과 구매자에게 " + point(event.amount()) + "를 전액 환불했어요."
                : "관리자 확인 결과 판매자에게 " + point(event.amount()) + "를 정산했어요.";
        // 환불은 구매자에게, 정산은 판매자에게 읽지 않은 메시지로 간다. (같은 대화방이라 두 사람 모두 볼 수 있다)
        Long senderId = event.refunded() ? event.sellerId() : event.buyerId();
        Long receiverId = event.refunded() ? event.buyerId() : event.sellerId();
        send(event.orderId(), senderId, receiverId, "order-review-result-" + event.orderId(),
                label(event.auctionId(), event.productName()) + result);
    }

    private static String issueLabel(OrderIssueType type) {
        if (type == null) {
            return "기타";
        }
        return switch (type) {
            case NOT_RECEIVED -> "상품을 받지 못함";
            case WRONG_ITEM -> "주문한 상품과 다름";
            case DAMAGED -> "상품 파손";
            case OTHER -> "기타";
        };
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
