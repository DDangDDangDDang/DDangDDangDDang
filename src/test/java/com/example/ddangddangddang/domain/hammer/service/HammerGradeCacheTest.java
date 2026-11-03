package com.example.ddangddangddang.domain.hammer.service;

import com.example.ddangddangddang.domain.hammer.entity.HammerGrade;
import com.example.ddangddangddang.domain.order.entity.Order;
import com.example.ddangddangddang.domain.order.entity.OrderStatus;
import com.example.ddangddangddang.domain.order.repository.OrderRepository;
import com.example.ddangddangddang.global.config.CacheConfig;
import com.example.ddangddangddang.support.MySqlIntegrationTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 망치 등급 캐시(Redis): 판매자 프로필을 부를 때마다 12개월치 주문을 다시 읽지 않도록 10분 캐시한다.
 * 다른 MySQL 통합 테스트와 컨텍스트를 함께 쓰도록 Redis 컨테이너를 따로 띄우지 않고 테스트용 Redis(DB 15)를 쓴다.
 * (컨텍스트가 늘면 테스트 JVM 메모리가 모자란다)
 */
class HammerGradeCacheTest extends MySqlIntegrationTest {

    @Autowired private HammerGradeService hammerGradeService;
    @Autowired private OrderRepository orderRepository;
    @Autowired private CacheManager cacheManager;

    @AfterEach
    void tearDown() {
        orderRepository.deleteAllInBatch();
        cacheManager.getCache(CacheConfig.HAMMER_GRADE).clear();
    }

    @Test
    @DisplayName("등급을 회원별로 캐시해서, 거래가 늘어도 캐시가 남아 있는 동안은 다시 계산하지 않는다")
    void cachesGradePerUser() {
        long userId = 7_000L + System.nanoTime() % 1_000;

        assertThat(hammerGradeService.gradeOf(userId)).isEqualTo(HammerGrade.NEWBIE);
        assertThat(cacheManager.getCache(CacheConfig.HAMMER_GRADE).get(userId)).isNotNull();

        for (long partner = 1; partner <= 3; partner++) {
            Order order = Order.create(70_000L + partner, userId, partner, "상품", null, 10_000L);
            ReflectionTestUtils.setField(order, "status", OrderStatus.COMPLETED);
            ReflectionTestUtils.setField(order, "completedAt", LocalDateTime.now().minusDays(partner));
            orderRepository.save(order);
        }
        assertThat(hammerGradeService.gradeOf(userId)).isEqualTo(HammerGrade.NEWBIE);

        cacheManager.getCache(CacheConfig.HAMMER_GRADE).evict(userId);
        assertThat(hammerGradeService.gradeOf(userId)).isEqualTo(HammerGrade.SILVER);
    }
}
