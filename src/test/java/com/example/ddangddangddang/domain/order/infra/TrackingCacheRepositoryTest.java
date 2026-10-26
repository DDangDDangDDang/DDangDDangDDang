package com.example.ddangddangddang.domain.order.infra;

import com.example.ddangddangddang.domain.order.entity.Courier;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 배송 조회 캐시를 실제 Redis에서 확인한다. Docker가 실행 중이어야 한다.
 */
@Testcontainers
class TrackingCacheRepositoryTest {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static TrackingCacheRepository repository;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        repository = new TrackingCacheRepository(redisTemplate, JsonMapper.builder().build());
    }

    @AfterAll
    static void tearDownRedis() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void clear() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushAll();
    }

    @Test
    @DisplayName("택배사·송장번호별로 저장하고 TTL과 함께 조회된다")
    void saveAndFind() {
        TrackingEvent event = new TrackingEvent("2026-10-26T18:10:00+09:00", "DELIVERED", "배달완료", "강남", null);
        TrackingInfo info = new TrackingInfo(event, List.of(event));

        repository.save(Courier.CJ, "123", info, Duration.ofMinutes(10));

        assertThat(repository.find(Courier.CJ, "123")).contains(info);
        assertThat(repository.find(Courier.LOTTE, "123")).isEmpty();
        assertThat(redisTemplate.getExpire("tracking:CJ:123")).isBetween(1L, 600L);
    }
}
