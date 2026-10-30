package com.example.ddangddangddang.domain.auction.service;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.BidStatus;
import com.example.ddangddangddang.domain.auction.repository.BidRepository;
import com.example.ddangddangddang.domain.order.dto.OrderCreateCommand;
import com.example.ddangddangddang.domain.order.service.OrderService;
import com.example.ddangddangddang.domain.point.service.PointService;
import com.example.ddangddangddang.domain.product.entity.Product;
import com.example.ddangddangddang.global.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 경매가 낙찰자와 함께 끝났을 때의 후속 처리. 마감 낙찰(AuctionSettlementListener)과 즉시구매(BuyNowService)가
 * 같은 규칙으로 정산되도록 모아뒀다.
 * 호출한 쪽(마감, 즉시구매)의 트랜잭션 안에서만 실행되어야 해서 MANDATORY로 강제한다.
 * 그래야 포인트 차감, 상품 판매 완료, 주문 생성이 경매 상태 변경과 함께 커밋되거나 함께 롤백된다.
 */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.MANDATORY)
public class AuctionSettlementService {

    private final PointService pointService;
    private final OrderService orderService;
    private final BidRepository bidRepository;
    private final FileStorage fileStorage;

    /** 마감 낙찰: 낙찰자의 홀딩 포인트를 차감하고 판매를 완료한다 */
    public Long settleWinningBid(Auction auction) {
        pointService.purchaseWithHeld(auction.getHighestBidderId(), auction.getCurrentPrice(), auction.getId());
        bidRepository.changeStatus(auction.getId(), BidStatus.ACTIVE, BidStatus.WON);
        return completeSale(auction);
    }

    /**
     * 상품 판매 완료 + 주문 생성. 포인트 처리는 호출한 쪽에서 끝낸 뒤 부른다.
     * 남아 있는 최고 입찰(ACTIVE)은 OUTBID로 바꾼다. 즉시구매로 끝나면 기존 최고 입찰이 여기서 밀려난다.
     * (마감 낙찰은 settleWinningBid가 먼저 WON으로 바꿔 두므로 바뀌는 행이 없다)
     */
    public Long completeSale(Auction auction) {
        bidRepository.changeStatus(auction.getId(), BidStatus.ACTIVE, BidStatus.OUTBID);
        Product product = auction.getProduct();
        product.sold();
        return orderService.createOrder(new OrderCreateCommand(
                auction.getId(),
                auction.getHighestBidderId(),
                product.getSeller().getId(),
                product.getName(),
                // 대표 이미지 주소를 주문에 남긴다. (판매된 상품은 수정해도 빠진 이미지를 지우지 않는다, ProductService)
                product.getThumbnailKey() == null ? null : fileStorage.publicUrl(product.getThumbnailKey()),
                auction.getCurrentPrice()
        ));
    }
}
