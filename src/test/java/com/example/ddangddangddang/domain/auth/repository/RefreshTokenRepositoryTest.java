package com.example.ddangddangddang.domain.auth.repository;

import com.example.ddangddangddang.domain.auth.repository.RefreshTokenRepository.RotateResult;
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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 재발급 시 토큰 확인과 교체를 한 번에 하는 Lua 스크립트를 실제 Redis에서 확인한다.
 * Docker가 실행 중이어야 한다.
 */
@Testcontainers
class RefreshTokenRepositoryTest {

    private static final Long USER_ID = 1L;
    private static final String KEY = "refresh:1";
    private static final Duration TTL = Duration.ofDays(14);

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static RefreshTokenRepository repository;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        repository = new RefreshTokenRepository(redisTemplate);
    }

    @AfterAll
    static void tearDownRedis() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void clear() {
        redisTemplate.delete(KEY);
    }

    @Test
    @DisplayName("저장된 토큰과 같으면 새 토큰으로 교체하고 TTL을 새로 건다")
    void rotated() {
        repository.save(USER_ID, "old-token", Duration.ofMinutes(1));

        RotateResult result = repository.rotate(USER_ID, "old-token", "new-token", TTL);

        assertThat(result).isEqualTo(RotateResult.ROTATED);
        assertThat(redisTemplate.opsForValue().get(KEY)).isEqualTo("new-token");
        assertThat(redisTemplate.getExpire(KEY, TimeUnit.SECONDS)).isGreaterThan(Duration.ofDays(13).toSeconds());
    }

    @Test
    @DisplayName("저장된 토큰이 없으면(로그아웃) 아무것도 저장하지 않는다")
    void notFound() {
        RotateResult result = repository.rotate(USER_ID, "old-token", "new-token", TTL);

        assertThat(result).isEqualTo(RotateResult.NOT_FOUND);
        assertThat(redisTemplate.hasKey(KEY)).isFalse();
    }

    @Test
    @DisplayName("다른 토큰이 저장돼 있으면(이미 교체된 토큰 재사용) 저장된 토큰까지 삭제한다")
    void reused() {
        repository.save(USER_ID, "current-token", TTL);

        RotateResult result = repository.rotate(USER_ID, "old-token", "new-token", TTL);

        assertThat(result).isEqualTo(RotateResult.REUSED);
        assertThat(redisTemplate.hasKey(KEY)).isFalse();
    }

    @Test
    @DisplayName("재발급과 로그아웃이 동시에 실행돼도 로그아웃 뒤에 Refresh Token이 남지 않는다")
    void logoutDuringReissue() throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            for (int i = 0; i < 200; i++) {
                repository.save(USER_ID, "token-" + i, TTL);
                CountDownLatch start = new CountDownLatch(1);

                String current = "token-" + i;
                String next = current + "-new";
                Future<RotateResult> reissue = executor.submit(() -> {
                    start.await();
                    return repository.rotate(USER_ID, current, next, TTL);
                });
                Future<?> logout = executor.submit(() -> {
                    start.await();
                    repository.deleteByUserId(USER_ID);
                    return null;
                });

                start.countDown();
                RotateResult result = reissue.get(5, TimeUnit.SECONDS);
                logout.get(5, TimeUnit.SECONDS);

                // 재발급이 먼저면 로그아웃이 새 토큰을 지우고, 로그아웃이 먼저면 재발급이 NOT_FOUND가 된다.
                assertThat(redisTemplate.hasKey(KEY))
                        .as("%d번째 시도 (재발급 결과: %s)", i, result)
                        .isFalse();
            }
        } finally {
            executor.shutdownNow();
        }
    }
}
