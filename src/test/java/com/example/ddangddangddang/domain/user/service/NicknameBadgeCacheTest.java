package com.example.ddangddangddang.domain.user.service;

import com.example.ddangddangddang.domain.user.entity.NicknameBadge;
import com.example.ddangddangddang.domain.user.entity.User;
import com.example.ddangddangddang.domain.user.entity.UserBadge;
import com.example.ddangddangddang.domain.user.repository.UserBadgeRepository;
import com.example.ddangddangddang.domain.user.repository.UserRepository;
import com.example.ddangddangddang.global.config.CacheConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 장착한 닉네임 뱃지 캐시(Redis): 라이브 채팅이 메시지마다 읽으므로 캐시하고, 장착을 바꾸면 바로 다음 메시지부터 바뀐 뱃지가 나온다.
 * 캐시가 실제로 Redis에 쓰이고 지워지는지 봐야 하므로 Redis를 Testcontainers로 띄운다. (CategoryCacheTest와 같은 방식)
 */
@SpringBootTest
@Testcontainers
class NicknameBadgeCacheTest {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private NicknameBadges nicknameBadges;

    @Autowired
    private UserBadgeService userBadgeService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserBadgeRepository userBadgeRepository;

    @Autowired
    private CacheManager cacheManager;

    @Test
    @DisplayName("장착한 뱃지를 캐시하고, 장착을 바꾸거나 빼면 캐시를 지워 바로 바뀐 뱃지가 나온다")
    void evictsOnEquip() {
        long seq = System.nanoTime() % 1_000_000;
        User user = userRepository.save(User.create("badge" + seq + "@test.com", "encoded", "뱃지" + seq));
        Long userId = user.getId();
        userBadgeRepository.save(UserBadge.of(userId, NicknameBadge.STAR));
        userBadgeRepository.save(UserBadge.of(userId, NicknameBadge.HALO));

        assertThat(nicknameBadges.equipped(userId).badge()).isNull();
        assertThat(cacheManager.getCache(CacheConfig.NICKNAME_BADGE).get(userId)).isNotNull();

        userBadgeService.equip(userId, NicknameBadge.STAR);
        assertThat(cacheManager.getCache(CacheConfig.NICKNAME_BADGE).get(userId)).isNull();
        assertThat(nicknameBadges.equipped(userId).badge()).isEqualTo(NicknameBadge.STAR);

        userBadgeService.equip(userId, NicknameBadge.HALO);
        assertThat(nicknameBadges.equipped(userId).badge()).isEqualTo(NicknameBadge.HALO);

        userBadgeService.equip(userId, null);
        assertThat(nicknameBadges.equipped(userId).badge()).isNull();
    }
}
