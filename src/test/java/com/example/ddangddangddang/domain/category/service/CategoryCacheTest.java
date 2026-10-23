package com.example.ddangddangddang.domain.category.service;

import com.example.ddangddangddang.domain.category.dto.request.CategoryRequest;
import com.example.ddangddangddang.domain.category.dto.request.UpdateRequest;
import com.example.ddangddangddang.domain.category.dto.response.CategoryResponse;
import com.example.ddangddangddang.global.config.CacheConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.cache.interceptor.SimpleKey;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 카테고리 목록 캐시(Redis): 관리자가 카테고리를 만들거나 고치거나 지우면 바로 다음 조회부터 바뀐 목록이 나온다.
 * Spring Data Redis 4는 기본으로 캐시를 비동기로 지워, 바꾸자마자 조회하면 예전 목록이 나왔다. (CacheConfig의 immediateWrites)
 * 그래서 기다리지 않고 바로 조회해 확인한다.
 * 캐시가 실제로 Redis에 쓰이고 지워지는지 봐야 하므로 Redis를 Testcontainers로 띄운다. (CI의 다른 테스트에는 Redis가 없음)
 */
@SpringBootTest
@Testcontainers
class CategoryCacheTest {

    @Container
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7.4")).withExposedPorts(6379);

    @DynamicPropertySource
    static void redisProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
    }

    @Autowired
    private CategoryService categoryService;

    @Autowired
    private CacheManager cacheManager;

    @Test
    @DisplayName("카테고리를 만들고·고치고·지우면 캐시가 바로 지워져 다음 조회에 바로 반영된다")
    void evictsImmediately() {
        String name = "캐시" + System.nanoTime() % 1_000_000;
        // 목록을 한 번 읽어 캐시에 올린다.
        categoryService.findAdd();
        assertThat(cacheManager.getCache(CacheConfig.CATEGORIES).get(SimpleKey.EMPTY)).isNotNull();

        CategoryResponse created = categoryService.creation(new CategoryRequest(null, name, 999));
        assertThat(cacheManager.getCache(CacheConfig.CATEGORIES).get(SimpleKey.EMPTY)).isNull();
        assertThat(names(categoryService.findAdd())).contains(name);

        String renamed = name + "수정";
        categoryService.updateCategory(created.id(), new UpdateRequest(renamed, 999));
        assertThat(names(categoryService.findAdd())).contains(renamed).doesNotContain(name);

        categoryService.delete(created.id());
        assertThat(names(categoryService.findAdd())).doesNotContain(renamed);
    }

    private static List<String> names(List<CategoryResponse> categories) {
        return categories.stream()
                .flatMap(c -> java.util.stream.Stream.concat(java.util.stream.Stream.of(c), c.children() == null ? java.util.stream.Stream.empty() : c.children().stream()))
                .map(CategoryResponse::name)
                .toList();
    }
}
