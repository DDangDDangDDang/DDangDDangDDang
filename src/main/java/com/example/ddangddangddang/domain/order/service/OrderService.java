package com.example.ddangddangddang.domain.order.service;

import com.example.ddangddangddang.domain.order.dto.OrderCreateCommand;
import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.exception.OrderErrorCode;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
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

    private Order findOrder(Long orderId) {
        return orderRepository.findById(orderId)
                .orElseThrow(() -> new BusinessException(OrderErrorCode.ORDER_NOT_FOUND));
    }
}