package com.example.ddangddangddang.domain.order.service;

import com.example.ddangddangddang.domain.address.service.UserAddressService;
import com.example.ddangddangddang.domain.livechat.moderation.ChatContentFilter;
import com.example.ddangddangddang.domain.order.commission.CommissionPolicy;
import com.example.ddangddangddang.domain.order.commission.SaleContext;
import com.example.ddangddangddang.domain.order.dto.OrderAddressRequest;
import com.example.ddangddangddang.domain.order.dto.OrderCreateCommand;
import com.example.ddangddangddang.domain.order.entity.Courier;
import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.event.OrderCanceledEvent;
import com.example.ddangddangddang.domain.order.event.OrderConfirmedEvent;
import com.example.ddangddangddang.domain.order.event.OrderDelayRequestedEvent;
import com.example.ddangddangddang.domain.order.event.OrderDelayRespondedEvent;
import com.example.ddangddangddang.domain.order.exception.OrderErrorCode;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import com.example.ddangddangddang.domain.point.service.PointService;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.hibernate.exception.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.ddangddangddang.domain.order.dto.OrderResponse;
import com.example.ddangddangddang.domain.order.dto.OrderSummaryResponse;
import com.example.ddangddangddang.domain.order.entity.OrderRole;
import com.example.ddangddangddang.domain.order.entity.OrderCancelReason;
import com.example.ddangddangddang.domain.order.entity.OrderStatus;
import com.example.ddangddangddang.global.common.CursorResponse;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {
    // 발송 후 이 기간이 지날시 자동으로 구매 확정
    static final int AUTO_CONFIRM_DAYS = 7;

    //자동 구매 확정이 이 횟수 만큼 실패하면 대상에서 빼고 사람이 확인 한다.
    static final int MAX_AUTO_CONFIRM_FAILURES = 10;

    private static final int MAX_PAGE_SIZE = 100;
    private final OrderRepository orderRepository;
    private final CommissionPolicy commissionPolicy;
    private final PointService pointService;
    private final ApplicationEventPublisher eventPublisher;
    private final ChatContentFilter contentFilter;
    private final UserAddressService userAddressService;

    /**
     * 낙찰 주문을 만든다. API가 아니라 경매 도메인이 호출하는 내부 메서드다.
     * <p>
     * 경매 종료 처리(낙찰자 포인트 차감)와 같은 트랜잭션에서 호출해야 한다.
     * 그래야 포인트는 빠졌는데 주문이 없거나, 주문은 있는데 포인트가 안 빠지는 상황이 생기지 않는다.
     *
     * @return 생성된 주문 ID
     */
    @Transactional
    public Long createOrder(OrderCreateCommand command) {
        if (orderRepository.existsByAuctionId(command.auctionId())) {
            throw new BusinessException(OrderErrorCode.ORDER_ALREADY_EXISTS);
        }

        // 판매 수수료율은 주문이 생길 때 정해 저장한다 (할인 규칙 포함, CommissionPolicy)
        int commissionRateBps = commissionPolicy.rateBpsFor(
                new SaleContext(command.auctionId(), command.sellerId(), command.buyerId(), command.finalPrice()));
        Order order = Order.create(
                command.auctionId(),
                command.buyerId(),
                command.sellerId(),
                command.productName(),
                command.productImageUrl(),
                command.finalPrice(),
                commissionRateBps
        );
        try {
            return orderRepository.saveAndFlush(order).getId();
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e, Order.UK_AUCTION_ID)) {
                throw new BusinessException(OrderErrorCode.ORDER_ALREADY_EXISTS);
            }
            throw e;
        }
    }

    private boolean isUniqueViolation(DataIntegrityViolationException e, String constraintName) {
        return e.getCause() instanceof ConstraintViolationException violation
                && violation.getConstraintName() != null
                && violation.getConstraintName().toLowerCase().contains(constraintName);
    }

    public CursorResponse<OrderSummaryResponse> getOrders(Long userId, OrderRole role, OrderStatus status,
                                                          Long cursor, int size) {
        int pageSize = Math.max(1, Math.min(size, MAX_PAGE_SIZE));
        PageRequest limit = PageRequest.of(0, pageSize + 1);

        List<Order> fetched = role == OrderRole.SELLER
                ? orderRepository.findSellerOrders(userId, status, cursor, limit)
                : orderRepository.findBuyerOrders(userId, status, cursor, limit);

        return CursorResponse.of(fetched, pageSize, OrderSummaryResponse::from, Order::getId);
    }

    /**
     * 구매자나 판매자만 조회할 수 있다.
     */
    public OrderResponse getOrder(Long userId, Long orderId) {
        Order order = findOrder(orderId);
        if (!order.isParticipant(userId)) {
            throw new BusinessException(OrderErrorCode.NOT_ORDER_PARTICIPANT);
        }
        return OrderResponse.from(order);
    }

    /**
     * 판매자가 구매자의 배송지로 만든 송장(택배사, 송장번호)을 입력해 발송 처리한다.
     *
     * 같은 송장이 이미 다른 주문에 있으면 거부한다. 먼저 조회로 확인하고,
     * 두 주문이 동시에 같은 송장을 등록하는 경우는 유니크 제약이 막는다.
     * 변경 감지는 커밋할 때 UPDATE를 보내므로, 제약 위반을 여기서 잡으려면 직접 flush해야 한다.
     */
    @Transactional
    public OrderResponse ship(Long userId, Long orderId, Courier courier, String trackingNo) {
        Order order = findOrderForUpdate(orderId);
        if (!order.isSeller(userId)) {
            throw new BusinessException(OrderErrorCode.NOT_ORDER_SELLER);
        }
        if (orderRepository.existsByCourierAndTrackingNo(courier, trackingNo)) {
            throw new BusinessException(OrderErrorCode.DUPLICATE_TRACKING_NO);
        }
        order.ship(courier, trackingNo);
        try {
            orderRepository.flush();
        } catch (DataIntegrityViolationException e) {
            if (isUniqueViolation(e, Order.UK_COURIER_TRACKING_NO)) {
                throw new BusinessException(OrderErrorCode.DUPLICATE_TRACKING_NO);
            }
            throw e;
        }
        return OrderResponse.from(order);
    }


    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    /**
     * 구매자가 구매 확정하면 에스크로에 묶여 있던 낙찰 금액을 판매자에게 정산한다.
     *
     * 주문을 먼저 잠그고 상태를 확인하므로, 동시에 여러 번 요청해도 정산은 한 번만 된다.
     * (주문 락 -> 판매자 지갑 락 순서로 잠근다)
     */
    @Transactional
    public OrderResponse confirmPurchase(Long userId, Long orderId) {
        Order order = findOrderForUpdate(orderId);
        if (!order.isBuyer(userId)) {
            throw new BusinessException(OrderErrorCode.NOT_ORDER_BUYER);
        }
        settlePurchase(order);
        return OrderResponse.from(order);
    }

    /**
     * 발송 후 7일이 지나도 구매 확정하지 않은 주문을 자동을 확정하고 정산한다. 스케줄러가 건별로 호출한다.
     *
     * 주문을 잠근 뒤 상태와 기한을 다시 확인하므로, 그 사이 구매자가 확정했거나 취소된 주문은 건너뛴다.
     *
     * @return 이번 호출로 확정했으면 true
     */
    @Transactional
    public boolean autoConfirm(Long orderId){
        Order order = findOrderForUpdate(orderId);
        LocalDateTime cutoff = LocalDateTime.now().minusDays(AUTO_CONFIRM_DAYS);
        if (order.getStatus() != OrderStatus.SHIPPING || order.getShippedAt().isAfter(cutoff)){
            return false;
        }
        settlePurchase(order);
        return true;
    }

    /**
     * 자동 구매 확정이 실패한 주문의 실패 횟수를 1 올린다
     * 확정이 실패하면 그 트랜잭션은 취소되므로, 횟수는 따로 새 트랜잭션에서 기록한다.
     *
     * @return 올린 뒤의 실패 횟수
     */
    @Transactional
    public int recordAutoConfirmFailure(Long orderId){
        Order order = findOrderForUpdate(orderId);
        order.recordAutoConfirmFailure();
        return order.getAutoConfirmFailCount();
    }

    // 구매 확정: 주문 확정 상태를 바꾸고, 수수료를 뺀 금액을 판매자에게 정산후, 구매자 포인트 적립을 알린다.
    private void settlePurchase(Order order){
        order.confirm();
        pointService.settle(order.getSellerId(), order.getSettlementAmount(), order.getId());
        eventPublisher.publishEvent(new OrderConfirmedEvent(order.getId(),order.getBuyerId(),order.getSellerId(),order.getFinalPrice()));
    }
    /**
     * 기한을 넘긴 주문을 취소하고 구매자에게 환불한다. 스케줄러가 건별로 호출한다.
     * 배송지 미입력이면 위약금을 판매자에게 주고 나머지를 환불하고, 미발송이면 전액 환불한다.
     *
     * 주문을 잠근 뒤 상태와 기한을 다시 확인하므로, 그 사이 발송됐거나 이미 취소된 주문은 건너뛴다.
     * 정산 전 취소라 판매자 정산금·수수료·마일리지는 건드릴 것이 없다.
     *
     * @return 이번 호출로 취소했으면 true
     */
    @Transactional
    public boolean cancelOverdue(Long orderId, OrderCancelReason reason) {
        Order order = findOrderForUpdate(orderId);
        LocalDateTime now = LocalDateTime.now();
        boolean overdue = reason == OrderCancelReason.BUYER_NO_ADDRESS
                ? order.isAddressOverdue(now) : order.isShipOverdue(now);
        if (order.getStatus() != OrderStatus.ESCROW || !overdue) {
            return false;
        }
        order.cancel(reason, now);
        long penalty = reason == OrderCancelReason.BUYER_NO_ADDRESS ? order.getNoAddressPenalty() : 0;
        long refund = order.getFinalPrice() - penalty;
        if (penalty > 0) {
            pointService.refundOrderWithPenalty(order.getBuyerId(), refund, order.getSellerId(), penalty, order.getId());
        } else {
            pointService.refundOrder(order.getBuyerId(), refund, order.getId());
        }
        eventPublisher.publishEvent(new OrderCanceledEvent(order.getId(), order.getAuctionId(), order.getBuyerId(),
                order.getSellerId(), order.getProductName(), reason, refund, penalty));
        return true;
    }

    /**
     * 판매자가 발송 지연을 신청한다. 사유는 메신저로 그대로 전달되므로, 외부 거래 유도를 여기서 막는다.
     */
    @Transactional
    public OrderResponse requestDelay(Long userId, Long orderId, String reason) {
        Order order = findOrderForUpdate(orderId);
        if (!order.isSeller(userId)) {
            throw new BusinessException(OrderErrorCode.NOT_ORDER_SELLER_DELAY);
        }
        String stripped = reason.strip();
        if (contentFilter.findViolation(stripped).isPresent()) {
            throw new BusinessException(OrderErrorCode.DELAY_REASON_NOT_ALLOWED);
        }
        order.requestDelay(stripped, LocalDateTime.now());
        eventPublisher.publishEvent(new OrderDelayRequestedEvent(order.getId(), order.getAuctionId(), order.getBuyerId(),
                order.getSellerId(), order.getProductName(), stripped));
        return OrderResponse.from(order);
    }

    /** 구매자가 지연을 승인하면 발송 기한이 7일 늘어난다. */
    @Transactional
    public OrderResponse approveDelay(Long userId, Long orderId) {
        Order order = findOrderForDelayResponse(userId, orderId);
        order.approveDelay(LocalDateTime.now());
        publishDelayResponded(order, true);
        return OrderResponse.from(order);
    }

    /** 구매자가 지연을 거절하면 기존 기한대로 진행한다. */
    @Transactional
    public OrderResponse rejectDelay(Long userId, Long orderId) {
        Order order = findOrderForDelayResponse(userId, orderId);
        order.rejectDelay(LocalDateTime.now());
        publishDelayResponded(order, false);
        return OrderResponse.from(order);
    }

    private Order findOrderForDelayResponse(Long userId, Long orderId) {
        Order order = findOrderForUpdate(orderId);
        if (!order.isBuyer(userId)) {
            throw new BusinessException(OrderErrorCode.NOT_ORDER_BUYER_DELAY);
        }
        return order;
    }

    private void publishDelayResponded(Order order, boolean approved) {
        eventPublisher.publishEvent(new OrderDelayRespondedEvent(order.getId(), order.getAuctionId(), order.getBuyerId(),
                order.getSellerId(), order.getProductName(), approved));
    }

    private Order findOrderForUpdate(Long orderId) {
        return orderRepository.findByIdForUpdate(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }

    /**
     * 구매자가 배송지를 입력한다.
     * 발송과 같은 주문 락을 잡아서, 판매자가 발송하는 순간 배송지가 바뀌는 일이 없게 한다.
     */
    @Transactional
    public OrderResponse registerDeliveryAddress(Long userId, Long orderId, OrderAddressRequest request) {
        Order order = findOrderForUpdate(orderId);
        if (!order.isBuyer(userId)) {
            throw new BusinessException(OrderErrorCode.NOT_ORDER_BUYER_ADDRESS);
        }
        order.registerDeliveryAddress(request.receiverName(), request.receiverPhone(),
                request.address(), request.deliveryMemo());
        userAddressService.saveAsDefaultIfNone(userId,request.receiverName(),request.receiverPhone(),request.address(),request.deliveryMemo());
        return OrderResponse.from(order);
    }
}