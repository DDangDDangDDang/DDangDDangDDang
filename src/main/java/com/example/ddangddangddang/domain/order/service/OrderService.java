package com.example.ddangddangddang.domain.order.service;

import com.example.ddangddangddang.domain.order.dto.OrderAddressRequest;
import com.example.ddangddangddang.domain.order.dto.OrderCreateCommand;
import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.exception.OrderErrorCode;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import com.example.ddangddangddang.domain.point.service.PointService;
import com.example.ddangddangddang.global.exception.BusinessException;
import org.hibernate.exception.ConstraintViolationException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.ddangddangddang.domain.order.dto.OrderResponse;
import com.example.ddangddangddang.domain.order.dto.OrderSummaryResponse;
import com.example.ddangddangddang.domain.order.entity.OrderRole;
import com.example.ddangddangddang.domain.order.entity.OrderStatus;
import com.example.ddangddangddang.global.common.CursorResponse;
import org.springframework.data.domain.PageRequest;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private static final int MAX_PAGE_SIZE = 100;
    private final OrderRepository orderRepository;
    private final PointService pointService;

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

        Order order = Order.create(
                command.auctionId(),
                command.buyerId(),
                command.sellerId(),
                command.productName(),
                command.productImageUrl(),
                command.finalPrice()
        );
        try {
            return orderRepository.saveAndFlush(order).getId();
        } catch (DataIntegrityViolationException e) {
            if (isAuctionIdUniqueViolation(e)) {
                throw new BusinessException(OrderErrorCode.ORDER_ALREADY_EXISTS);
            }
            throw e;
        }
    }

    private boolean isAuctionIdUniqueViolation(DataIntegrityViolationException e) {
        return e.getCause() instanceof ConstraintViolationException violation
                && violation.getConstraintName() != null
                && violation.getConstraintName().toLowerCase().contains(Order.UK_AUCTION_ID);
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

    /** 판매자가 발송 처리한다. */
    @Transactional
    public OrderResponse ship(Long userId, Long orderId, String trackingNo) {
        Order order = findOrderForUpdate(orderId);
        if (!order.isSeller(userId)) {
            throw new BusinessException(OrderErrorCode.NOT_ORDER_SELLER);
        }
        order.ship(trackingNo);
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
        order.confirm();
        pointService.settle(order.getSellerId(), order.getFinalPrice(), order.getId());
        return OrderResponse.from(order);
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
        return OrderResponse.from(order);
    }
}