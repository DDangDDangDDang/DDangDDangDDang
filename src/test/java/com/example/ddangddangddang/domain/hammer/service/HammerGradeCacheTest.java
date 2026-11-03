package com.example.ddangddangddang.domain.hammer.service;

import com.example.ddangddangddang.domain.hammer.entity.HammerGrade;
import com.example.ddangddangddang.domain.hammer.repository.HammerScoreRepository;
import com.example.ddangddangddang.domain.order.entity.OrderStatus;
import com.example.ddangddangddang.global.config.CacheConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * 망치 등급 캐시: 판매자 프로필을 부를 때마다 12개월치 주문을 다시 읽지 않도록 회원별로 캐시한다.
 * Redis 없이(CI에는 Redis가 없다) 등급 서비스와 메모리 캐시만 띄워 캐시가 걸리는지 본다. (전체 컨텍스트를 띄우지 않아 가볍다)
 */
@SpringJUnitConfig(HammerGradeCacheTest.Config.class)
class HammerGradeCacheTest {

    @Configuration
    @EnableCaching
    @Import(HammerGradeService.class)
    static class Config {
        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(CacheConfig.HAMMER_GRADE);
        }
    }

    @Autowired private HammerGradeService hammerGradeService;
    @Autowired private CacheManager cacheManager;
    @MockitoBean private HammerScoreRepository hammerScoreRepository;

    @Test
    @DisplayName("같은 회원의 등급은 캐시에서 주고, 다른 회원은 따로 계산한다")
    void cachesGradePerUser() {
        assertThat(hammerGradeService.gradeOf(1L)).isEqualTo(HammerGrade.NEWBIE);
        assertThat(hammerGradeService.gradeOf(1L)).isEqualTo(HammerGrade.NEWBIE);
        assertThat(cacheManager.getCache(CacheConfig.HAMMER_GRADE).get(1L)).isNotNull();
        verify(hammerScoreRepository, times(1)).countByBuyerIdAndStatus(1L, OrderStatus.COMPLETED);

        hammerGradeService.gradeOf(2L);
        verify(hammerScoreRepository, times(1)).countByBuyerIdAndStatus(2L, OrderStatus.COMPLETED);
    }
}
