package com.example.ddangddangddang.domain.auction;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.category.entity.Category;
import com.example.ddangddangddang.domain.product.entity.Product;
import com.example.ddangddangddang.domain.user.entity.User;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

/** 단위 테스트용 경매 생성 도우미. DB 없이 id만 채워서 만든다. */
public final class AuctionFixture {

    public static final Long SELLER_ID = 1L;
    public static final Long AUCTION_ID = 100L;
    public static final long START_PRICE = 10_000L;
    public static final long BID_UNIT = 1_000L;
    public static final long BUY_NOW_PRICE = 100_000L;

    private AuctionFixture() {
    }

    /** 진행 중이고 종료까지 endsIn 남은 경매 */
    public static Auction ongoing(LocalDateTime now, java.time.Duration endsIn, Long buyNowPrice) {
        User seller = User.create("seller@test.com", "pw", "seller");
        ReflectionTestUtils.setField(seller, "id", SELLER_ID);
        Product product = new Product(seller, new Category(null, "전자기기", 1), "아이패드", "설명");
        ReflectionTestUtils.setField(product, "id", 10L);

        Auction auction = new Auction(product, null, START_PRICE, BID_UNIT, buyNowPrice,
                now.minusHours(1), now.plus(endsIn));
        auction.start();
        ReflectionTestUtils.setField(auction, "id", AUCTION_ID);
        ReflectionTestUtils.setField(auction, "version", 0L);
        return auction;
    }

    public static Auction ongoing(LocalDateTime now) {
        return ongoing(now, java.time.Duration.ofHours(1), BUY_NOW_PRICE);
    }

    /** 이미 누군가 입찰한 상태로 만든다 */
    public static void bidBy(Auction auction, Long bidderId, long amount) {
        ReflectionTestUtils.setField(auction, "highestBidderId", bidderId);
        ReflectionTestUtils.setField(auction, "currentPrice", amount);
        ReflectionTestUtils.setField(auction, "bidCount", auction.getBidCount() + 1);
    }
}
