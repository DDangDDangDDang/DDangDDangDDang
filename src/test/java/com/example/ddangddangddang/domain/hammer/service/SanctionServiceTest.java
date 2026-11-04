package com.example.ddangddangddang.domain.hammer.service;

import com.example.ddangddangddang.domain.auction.dto.request.AuctionCreateRequest;
import com.example.ddangddangddang.domain.auction.dto.request.LiveAuctionCreateRequest;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import com.example.ddangddangddang.domain.auction.service.AuctionService;
import com.example.ddangddangddang.domain.category.entity.Category;
import com.example.ddangddangddang.domain.category.repository.CategoryRepository;
import com.example.ddangddangddang.domain.product.entity.Product;
import com.example.ddangddangddang.domain.product.repository.ProductRepository;
import com.example.ddangddangddang.domain.auction.service.BidFacade;
import com.example.ddangddangddang.domain.auction.service.BuyNowService;
import com.example.ddangddangddang.domain.hammer.dto.AdminSanctionResponse;
import com.example.ddangddangddang.domain.hammer.dto.SanctionResponse;
import com.example.ddangddangddang.domain.hammer.entity.SanctionType;
import com.example.ddangddangddang.domain.hammer.exception.SanctionErrorCode;
import com.example.ddangddangddang.domain.hammer.repository.SanctionReleaseRepository;
import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.entity.OrderCancelReason;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.exception.BusinessException;
import com.example.ddangddangddang.support.MySqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SanctionServiceTest extends MySqlIntegrationTest {

    private static final AtomicLong AUCTION_ID = new AtomicLong(5_000);

    @Autowired private SanctionService sanctionService;
    @Autowired private SanctionReleaseRepository sanctionReleaseRepository;
    @Autowired private OrderRepository orderRepository;
    @Autowired private UserRepository userRepository;
    @Autowired private BidFacade bidFacade;
    @Autowired private BuyNowService buyNowService;
    @Autowired private AuctionService auctionService;
    @Autowired private AuctionRepository auctionRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private CategoryRepository categoryRepository;

    private User buyer;
    private User seller;

    @org.springframework.beans.factory.annotation.Autowired
    private com.example.ddangddangddang.domain.seller.repository.SellerApplicationRepository sellerApplicationRepository;
    private Product product;
    private LocalDateTime now;
    private Object originalCountedFrom;

    @BeforeEach
    void setUp() {
        buyer = userRepository.save(User.create("sanction-buyer@test.com", "pw", "sanctionBuyer", "01055556666"));
        seller = userRepository.save(User.create("sanction-seller@test.com", "pw", "sanctionSeller", "01077778888"));
        com.example.ddangddangddang.support.SellerFixture.verify(sellerApplicationRepository, seller.getId());
        Category category = categoryRepository.save(new Category(null, "전자기기", 1));
        product = productRepository.save(new Product(seller, category, "아이패드", "설명"));
        // 시행일 전 취소는 세지 않는다. 테스트에서는 아주 옛날로 두고, 끝나면 되돌린다 (컨텍스트를 함께 쓰는 다른 테스트에 영향이 없게)
        originalCountedFrom = ReflectionTestUtils.getField(sanctionService, "countedFrom");
        ReflectionTestUtils.setField(sanctionService, "countedFrom", LocalDateTime.of(2000, 1, 1, 0, 0));
        now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
    }

    @AfterEach
    void tearDown() {
        ReflectionTestUtils.setField(sanctionService, "countedFrom", originalCountedFrom);
        sanctionReleaseRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
        auctionRepository.deleteAllInBatch();
        productRepository.deleteAllInBatch();
        categoryRepository.deleteAllInBatch();
        sellerApplicationRepository.deleteAllInBatch();
        userRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("구매자 책임 취소가 90일 안에 2회면 입찰·즉시 구매가 7일 막힌다")
    void buyerBidBan() {
        canceled(buyer, seller, OrderCancelReason.BUYER_NO_ADDRESS, now.minusDays(20));
        canceled(buyer, seller, OrderCancelReason.BUYER_NO_ADDRESS, now.minusDays(1));

        assertThat(sanctionService.mySanctions(buyer.getId()))
                .extracting(SanctionResponse::type, SanctionResponse::bannedUntil, SanctionResponse::cancelCount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(SanctionType.BID_BAN, now.minusDays(1).plusDays(7), 2));
        Long auctionId = auctionService.auctionCreate(seller.getId(), auctionRequest()).id();
        assertBanned(() -> bidFacade.placeBid(buyer.getId(), auctionId, 10_000L), SanctionErrorCode.BID_BANNED);
        assertBanned(() -> buyNowService.buyNow(buyer.getId(), auctionId), SanctionErrorCode.BID_BANNED);
        // 판매는 막지 않는다
        assertThat(sanctionService.mySanctions(seller.getId())).isEmpty();
    }

    @Test
    @DisplayName("판매자 미발송 취소가 90일 안에 2회면 경매 등록이 막힌다")
    void sellerListingBan() {
        canceled(buyer, seller, OrderCancelReason.SELLER_NOT_SHIPPED, now.minusDays(5));
        canceled(buyer, seller, OrderCancelReason.SELLER_NOT_SHIPPED, now.minusDays(4));

        assertBanned(() -> auctionService.auctionCreate(seller.getId(), auctionRequest()), SanctionErrorCode.LISTING_BANNED);
        assertBanned(() -> auctionService.createLiveAuction(seller.getId(), 999_999L,
                new LiveAuctionCreateRequest(product.getId(), 10_000L, 1_000L, null, 300)), SanctionErrorCode.LISTING_BANNED);
        assertThat(auctionRepository.count()).isZero();
        assertThat(sanctionService.mySanctions(buyer.getId())).isEmpty();
    }

    @Test
    @DisplayName("정지 기간이 지나면 풀린다")
    void banExpires() {
        canceled(buyer, seller, OrderCancelReason.BUYER_NO_ADDRESS, now.minusDays(20));
        canceled(buyer, seller, OrderCancelReason.BUYER_NO_ADDRESS, now.minusDays(8));

        assertThat(sanctionService.mySanctions(buyer.getId())).isEmpty();
    }

    @Test
    @DisplayName("시행일 전 취소는 세지 않는다")
    void ignoresCancelsBeforeCountedFrom() {
        ReflectionTestUtils.setField(sanctionService, "countedFrom", now.minusDays(2));
        canceled(buyer, seller, OrderCancelReason.BUYER_NO_ADDRESS, now.minusDays(3));
        canceled(buyer, seller, OrderCancelReason.BUYER_NO_ADDRESS, now.minusDays(1));

        assertThat(sanctionService.mySanctions(buyer.getId())).isEmpty();
    }

    @Test
    @DisplayName("4회 이상이면 관리자 검토 목록에 나오고, 관리자가 풀면 그때까지의 취소는 다시 세지 않는다")
    void reviewAndRelease() {
        for (int day = 4; day >= 1; day--) {
            canceled(buyer, seller, OrderCancelReason.BUYER_NO_ADDRESS, now.minusDays(day));
        }

        List<AdminSanctionResponse> targets = sanctionService.reviewTargets();
        assertThat(targets).extracting(AdminSanctionResponse::userId, AdminSanctionResponse::nickname,
                        AdminSanctionResponse::type, AdminSanctionResponse::cancelCount)
                .containsExactly(org.assertj.core.groups.Tuple.tuple(buyer.getId(), "sanctionBuyer", SanctionType.BID_BAN, 4));

        sanctionService.release(buyer.getId(), SanctionType.BID_BAN);

        assertThat(sanctionService.mySanctions(buyer.getId())).isEmpty();
        assertThat(sanctionService.reviewTargets()).isEmpty();

        // 풀린 뒤 다시 취소되면 1회부터 센다
        canceled(buyer, seller, OrderCancelReason.BUYER_NO_ADDRESS, LocalDateTime.now().plusSeconds(1));
        assertThat(sanctionService.mySanctions(buyer.getId())).isEmpty();
    }

    private void assertBanned(org.assertj.core.api.ThrowableAssert.ThrowingCallable call, SanctionErrorCode code) {
        assertThatThrownBy(call)
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode").isEqualTo(code);
    }

    private AuctionCreateRequest auctionRequest() {
        LocalDateTime startAt = LocalDateTime.now().plusHours(1);
        return new AuctionCreateRequest(product.getId(), 10_000L, 1_000L, 50_000L, startAt, startAt.plusHours(1));
    }

    private void canceled(User buyer, User seller, OrderCancelReason reason, LocalDateTime canceledAt) {
        Order order = Order.create(AUCTION_ID.incrementAndGet(), buyer.getId(), seller.getId(), "상품", null, 10_000L);
        order.cancel(reason, canceledAt);
        orderRepository.save(order);
    }
}
