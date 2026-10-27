package com.example.ddangddangddang.global.config;

import lombok.extern.slf4j.Slf4j;
import org.jspecify.annotations.Nullable;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

import java.util.Collection;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 캐시에 쓰거나 지우다 실패해도(Redis가 잠깐 안 됨) 요청을 실패시키지 않고 로그만 남긴다.
 *
 * 캐시 오류 처리기(LoggingCacheErrorHandler)는 @Cacheable·@CacheEvict가 직접 부를 때만 오류를 잡는다.
 * 트랜잭션 인식 캐시는 쓰기·지우기를 커밋 뒤(afterCommit)로 미루는데, 그때 난 오류는 오류 처리기를 거치지 않고
 * 그대로 요청까지 올라가 500이 된다. 그래서 캐시 자체에서 쓰기·지우기 오류를 삼킨다.
 * 읽기 오류는 그대로 던져 오류 처리기가 로그를 남기고 DB에서 읽게 한다.
 */
@Slf4j
class ResilientCacheManager implements CacheManager {

    private final CacheManager target;
    private final Map<String, Cache> caches = new ConcurrentHashMap<>();

    ResilientCacheManager(CacheManager target) {
        this.target = target;
    }

    @Override
    public @Nullable Cache getCache(String name) {
        Cache cache = target.getCache(name);
        return cache == null ? null : caches.computeIfAbsent(name, n -> new ResilientCache(cache));
    }

    @Override
    public Collection<String> getCacheNames() {
        return target.getCacheNames();
    }

    private record ResilientCache(Cache target) implements Cache {

        @Override
        public String getName() {
            return target.getName();
        }

        @Override
        public Object getNativeCache() {
            return target.getNativeCache();
        }

        @Override
        public @Nullable ValueWrapper get(Object key) {
            return target.get(key);
        }

        @Override
        public <T> @Nullable T get(Object key, @Nullable Class<T> type) {
            return target.get(key, type);
        }

        @Override
        public <T> @Nullable T get(Object key, Callable<T> valueLoader) {
            return target.get(key, valueLoader);
        }

        @Override
        public void put(Object key, @Nullable Object value) {
            try {
                target.put(key, value);
            } catch (RuntimeException e) {
                warn("저장", key, e);
            }
        }

        @Override
        public @Nullable ValueWrapper putIfAbsent(Object key, @Nullable Object value) {
            try {
                return target.putIfAbsent(key, value);
            } catch (RuntimeException e) {
                warn("저장", key, e);
                return null;
            }
        }

        @Override
        public void evict(Object key) {
            try {
                target.evict(key);
            } catch (RuntimeException e) {
                warn("삭제", key, e);
            }
        }

        @Override
        public boolean evictIfPresent(Object key) {
            try {
                return target.evictIfPresent(key);
            } catch (RuntimeException e) {
                warn("삭제", key, e);
                return false;
            }
        }

        @Override
        public void clear() {
            try {
                target.clear();
            } catch (RuntimeException e) {
                warn("비우기", "*", e);
            }
        }

        @Override
        public boolean invalidate() {
            try {
                return target.invalidate();
            } catch (RuntimeException e) {
                warn("비우기", "*", e);
                return false;
            }
        }

        private void warn(String action, Object key, RuntimeException e) {
            // 지우지 못한 캐시는 TTL(10분)이 지나면 다시 만들어진다.
            log.warn("캐시 {}에 실패했습니다. cache={}, key={}", action, target.getName(), key, e);
        }
    }
}
