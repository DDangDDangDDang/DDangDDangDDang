package com.example.ddangddangddang.domain.order.service;

import com.example.ddangddangddang.domain.order.dto.OrderCreateCommand;
import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.exception.OrderErrorCode;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OrderService {

    private final OrderRepository orderRepository;

    /**
     * 낙찰 주문을 만든다. API가 아니라 경매 도메인이 호출하는 내부 메서드다.
     *
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
        return orderRepository.save(order).getId();
    }
}