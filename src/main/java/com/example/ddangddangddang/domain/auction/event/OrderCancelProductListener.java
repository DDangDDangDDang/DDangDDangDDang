package com.example.ddangddangddang.domain.auction.event;

import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.order.event.OrderCanceledEvent;
import com.example.ddangddangddang.domain.product.entity.Product;
import com.example.ddangddangddang.domain.product.entity.Status;
import com.example.ddangddangddang.domain.product.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 낙찰 주문이 취소되면 판매자가 다시 경매에 올릴 수 있도록 상품을 판매 중으로 돌린다.
 * 경매 자체는 ENDED로 남아 이력에 취소 사유와 함께 보인다.
 *
 * BEFORE_COMMIT이라 취소·환불과 같은 트랜잭션에서 실행된다. (주문 락 -> 상품 락 순서로 잠근다)
 */
@Component
@RequiredArgsConstructor
public class OrderCancelProductListener {

    private final AuctionRepository auctionRepository;
    private final ProductRepository productRepository;

    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void relistProduct(OrderCanceledEvent event) {
        auctionRepository.findById(event.auctionId())
                .flatMap(auction -> productRepository.findWithLockByIdAndStatusNot(auction.getProduct().getId(), Status.DELETED))
                .ifPresent(Product::relist);
    }
}
