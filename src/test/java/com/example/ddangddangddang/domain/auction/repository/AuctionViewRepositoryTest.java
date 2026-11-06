package com.example.ddangddangddang.domain.auction.repository;

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

import java.time.Duration;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 조회수를 Redis에 모으고, DB로 옮긴 만큼 빼는 동작을 실제 Redis에서 확인한다.
 * Docker가 실행 중이어야 한다.
 */
@Testcontainers
class AuctionViewRepositoryTest {

    private static final Duration TTL = Duration.ofMinutes(30);

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static AuctionViewRepository repository;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        repository = new AuctionViewRepository(redisTemplate);
    }

    @AfterAll
    static void tearDownRedis() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void clear() {
        redisTemplate.getConnectionFactory().getConnection().serverCommands().flushDb();
    }

    @Test
    @DisplayName("같은 사람이 30분 안에 다시 보면 한 번만 센다")
    void countsOncePerViewer() {
        repository.increaseIfFirstView(5L, "user:2", TTL);
        repository.increaseIfFirstView(5L, "user:2", TTL);
        repository.increaseIfFirstView(5L, "user:3", TTL);

        assertThat(repository.allViewCounts()).isEqualTo(Map.of(5L, 2L));
    }

    @Test
    @DisplayName("옮긴 만큼 빼서 0이 되면 Redis에서 지운다")
    void removesWhenZero() {
        addViews(5L, 4);

        repository.subtract(Map.of(5L, 4L));

        assertThat(repository.allViewCounts()).isEmpty();
    }

    @Test
    @DisplayName("옮기는 사이에 새로 본 사람 수는 남는다")
    void keepsViewsAddedDuringMove() {
        addViews(5L, 4);
        Map<Long, Long> moved = repository.allViewCounts();

        // DB에 옮기는 사이에 1명이 더 봤다
        addViews(5L, 1);
        repository.subtract(moved);

        assertThat(repository.allViewCounts()).isEqualTo(Map.of(5L, 1L));
    }

    // 서로 다른 사람이 count명 본 것처럼 조회수를 올린다
    private void addViews(Long auctionId, int count) {
        for (int i = 0; i < count; i++) {
            repository.increaseIfFirstView(auctionId, "user:" + System.nanoTime() + i, TTL);
        }
    }
}