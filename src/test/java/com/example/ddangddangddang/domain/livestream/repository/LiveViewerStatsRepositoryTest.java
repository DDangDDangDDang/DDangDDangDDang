package com.example.ddangddangddang.domain.livestream.repository;

import com.example.ddangddangddang.domain.livestream.repository.LiveViewerStatsRepository.Ranked;
import com.example.ddangddangddang.domain.livestream.repository.LiveViewerStatsRepository.Sample;
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
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 시청자 수 순위·기록·잠금을 실제 Redis에서 확인한다. Docker가 실행 중이어야 한다.
 */
@Testcontainers
class LiveViewerStatsRepositoryTest {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static StringRedisTemplate redisTemplate;
    private static LiveViewerStatsRepository repository;

    @BeforeAll
    static void setUpRedis() {
        connectionFactory = new LettuceConnectionFactory(REDIS.getHost(), REDIS.getMappedPort(6379));
        connectionFactory.afterPropertiesSet();
        connectionFactory.start();
        redisTemplate = new StringRedisTemplate(connectionFactory);
        redisTemplate.afterPropertiesSet();
        repository = new LiveViewerStatsRepository(redisTemplate);
    }

    @AfterAll
    static void tearDownRedis() {
        connectionFactory.destroy();
    }

    @BeforeEach
    void clear() {
        redisTemplate.delete(redisTemplate.keys("*"));
    }

    @Test
    @DisplayName("순위를 통째로 바꾸면 이전 집계에만 있던(끝난) 방송은 빠지고, 시청자 많은 순으로 읽힌다")
    void replaceAndTopViewers() {
        repository.replaceRankings(Map.of(1L, 10, 2L, 50, 3L, 30), Map.of());
        repository.replaceRankings(Map.of(1L, 15, 2L, 40), Map.of());

        assertThat(repository.topViewers(10)).containsExactly(new Ranked(2L, 40), new Ranked(1L, 15));
        assertThat(repository.topViewers(1)).containsExactly(new Ranked(2L, 40));
        // 호출마다 고유한 임시 키를 쓰고, RENAME으로 옮겨 남지 않는다.
        assertThat(redisTemplate.keys("live:viewers*:tmp:*")).isEmpty();
    }

    @Test
    @DisplayName("급증 순위에는 늘어난 방송(1 이상)만 증가 수 큰 순으로 나온다")
    void topSurging() {
        repository.replaceRankings(Map.of(1L, 10, 2L, 20, 3L, 30), Map.of(1L, 5, 2L, 0, 3L, -4));

        assertThat(repository.topSurging(10)).containsExactly(new Ranked(1L, 5));
    }

    @Test
    @DisplayName("방송별 시청자 수·증가 수를 한 번에 읽고, 집계되지 않은 방송은 결과에 없다")
    void scores() {
        repository.replaceRankings(Map.of(1L, 10, 2L, 20), Map.of(1L, 3, 2L, -1));

        assertThat(repository.getViewerCounts(List.of(1L, 2L, 9L))).isEqualTo(Map.of(1L, 10, 2L, 20));
        assertThat(repository.getIncreases(List.of(2L, 9L))).isEqualTo(Map.of(2L, -1));
    }

    @Test
    @DisplayName("진행 중인 방송이 없으면 순위가 비워진다")
    void emptyRankings() {
        repository.replaceRankings(Map.of(1L, 10), Map.of(1L, 2));
        repository.replaceRankings(Map.of(), Map.of());

        assertThat(repository.topViewers(10)).isEmpty();
        assertThat(repository.topSurging(10)).isEmpty();
    }

    @Test
    @DisplayName("기록은 최신이 앞에 오고, 정한 개수까지만 남으며 TTL이 걸린다")
    void samples() {
        for (int i = 1; i <= 5; i++) {
            repository.appendSample(1L, new Sample(i * 1000L, i * 10), 3, Duration.ofMinutes(6));
        }

        assertThat(repository.getSamples(1L)).containsExactly(
                new Sample(5000L, 50), new Sample(4000L, 40), new Sample(3000L, 30));
        assertThat(redisTemplate.getExpire("live:viewers:history:1")).isPositive();
        assertThat(repository.getSamples(2L)).isEmpty();
    }

    @Test
    @DisplayName("잠금은 한 번만 잡히고, TTL이 지나면 다시 잡을 수 있다")
    void lock() throws InterruptedException {
        assertThat(repository.tryLock(Duration.ofMillis(300))).isTrue();
        assertThat(repository.tryLock(Duration.ofMillis(300))).isFalse();

        Thread.sleep(400);

        assertThat(repository.tryLock(Duration.ofMillis(300))).isTrue();
    }
}
