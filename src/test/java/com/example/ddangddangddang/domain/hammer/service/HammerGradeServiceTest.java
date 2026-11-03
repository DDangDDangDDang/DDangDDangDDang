package com.example.ddangddangddang.domain.hammer.service;

import com.example.ddangddangddang.domain.hammer.entity.HammerGrade;
import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.entity.OrderCancelReason;
import com.example.ddangddangddang.domain.order.entity.OrderStatus;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import com.example.ddangddangddang.domain.review.entity.Review;
import com.example.ddangddangddang.domain.review.repository.ReviewRepository;
import com.example.ddangddangddang.global.config.CacheConfig;
import com.example.ddangddangddang.support.MySqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class HammerGradeServiceTest extends MySqlIntegrationTest {

    private static final long USER = 1_000L;
    private static final AtomicLong AUCTION_ID = new AtomicLong(1_000);

    @Autowired private HammerGradeService hammerGradeService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private ReviewRepository reviewRepository;
    @Autowired private CacheManager cacheManager;

    // 테스트마다 같은 회원 번호를 쓰므로 앞 테스트의 등급 캐시를 지운다
    @BeforeEach
    void clearCache() {
        cacheManager.getCache(CacheConfig.HAMMER_GRADE).clear();
    }

    @AfterEach
    void tearDown() {
        reviewRepository.deleteAllInBatch();
        orderRepository.deleteAllInBatch();
    }

    @Test
    @DisplayName("구매 확정 거래가 3건 미만이면 새 망치")
    void newbie() {
        completed(USER, 2L, LocalDateTime.now().minusDays(1));

        assertThat(hammerGradeService.gradeOf(USER)).isEqualTo(HammerGrade.NEWBIE);
    }

    @Test
    @DisplayName("판매자로 받은 리뷰와 최근 판매자 미발송 취소를 반영한다")
    void sellerReviewsAndNotShippedCancels() {
        LocalDateTime now = LocalDateTime.now();
        review(completed(2L, USER, now.minusDays(3)), 1);
        review(completed(3L, USER, now.minusDays(2)), 1);
        review(completed(4L, USER, now.minusDays(1)), 1);
        canceled(5L, USER, OrderCancelReason.SELLER_NOT_SHIPPED, now.minusDays(10));

        // 50 + 3 - 6 - 5 = 42 (리뷰를 빼먹으면 48 은 망치)
        assertThat(hammerGradeService.gradeOf(USER)).isEqualTo(HammerGrade.STONE);
    }

    @Test
    @DisplayName("12개월이 지난 취소는 빼고, 지난 거래도 새 망치 판단에는 센다")
    void ignoresCancelsOlderThanAYear() {
        LocalDateTime now = LocalDateTime.now();
        completed(USER, 2L, now.minusDays(400));
        completed(USER, 3L, now.minusDays(2));
        completed(USER, 4L, now.minusDays(1));
        canceled(USER, 5L, OrderCancelReason.BUYER_NO_ADDRESS, now.minusDays(400));
        canceled(USER, 6L, OrderCancelReason.BUYER_NO_ADDRESS, now.minusDays(400));

        // 50 + 2 = 52 (오래된 취소를 세면 42 돌 망치)
        assertThat(hammerGradeService.gradeOf(USER)).isEqualTo(HammerGrade.SILVER);
    }

    @Test
    @DisplayName("다른 사람 책임으로 취소된 주문은 깎지 않는다")
    void otherPartysCancelDoesNotCount() {
        LocalDateTime now = LocalDateTime.now();
        completed(USER, 2L, now.minusDays(3));
        completed(USER, 3L, now.minusDays(2));
        completed(USER, 4L, now.minusDays(1));
        // 내가 구매자인데 판매자가 안 보낸 취소, 내가 판매자인데 구매자가 배송지를 안 넣은 취소
        canceled(USER, 5L, OrderCancelReason.SELLER_NOT_SHIPPED, now.minusDays(5));
        canceled(6L, USER, OrderCancelReason.BUYER_NO_ADDRESS, now.minusDays(5));
        canceled(7L, USER, OrderCancelReason.BUYER_NO_ADDRESS, now.minusDays(5));

        assertThat(hammerGradeService.gradeOf(USER)).isEqualTo(HammerGrade.SILVER);
    }

    private Order completed(Long buyerId, Long sellerId, LocalDateTime completedAt) {
        Order order = Order.create(AUCTION_ID.incrementAndGet(), buyerId, sellerId, "상품", null, 10_000L);
        ReflectionTestUtils.setField(order, "status", OrderStatus.COMPLETED);
        ReflectionTestUtils.setField(order, "completedAt", completedAt);
        return orderRepository.save(order);
    }

    private void canceled(Long buyerId, Long sellerId, OrderCancelReason reason, LocalDateTime canceledAt) {
        Order order = Order.create(AUCTION_ID.incrementAndGet(), buyerId, sellerId, "상품", null, 10_000L);
        order.cancel(reason, canceledAt);
        orderRepository.save(order);
    }

    private void review(Order order, int rating) {
        reviewRepository.save(Review.create(order, rating, null));
    }
}
