package com.example.ddangddangddang.global.config;

import com.example.ddangddangddang.domain.category.dto.response.CategoryResponse;
import org.springframework.cache.annotation.CachingConfigurer;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.interceptor.CacheErrorHandler;
import org.springframework.cache.interceptor.LoggingCacheErrorHandler;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.serializer.JacksonJsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import tools.jackson.databind.JavaType;
import tools.jackson.databind.json.JsonMapper;
import java.time.Duration;
import java.util.List;

@Configuration
@EnableCaching
public class CacheConfig implements CachingConfigurer {

    //캐시 이름
    public static final String CATEGORIES ="categories";

    @Bean
    public RedisCacheManager cacheManager(RedisConnectionFactory connectionFactory){
        //카테고리 목록(List<CategoryResponse>)을 JSON으로 저장 및 복원
        JsonMapper jsonMapper = JsonMapper.builder().build();
        JavaType categoryListType = jsonMapper.getTypeFactory()
                .constructCollectionType(List.class, CategoryResponse.class);

        RedisCacheConfiguration categoryConfig =RedisCacheConfiguration.defaultCacheConfig()
                .entryTtl(Duration.ofMinutes(10)) //삭제를 놓쳐도 10분 뒤 자동 새로 만들기
                .disableCachingNullValues()
                .serializeValuesWith(RedisSerializationContext.SerializationPair.fromSerializer(
                        new JacksonJsonRedisSerializer<>(jsonMapper,categoryListType)
                ));
        return RedisCacheManager.builder(connectionFactory)
                .withCacheConfiguration(CATEGORIES, categoryConfig)
                .transactionAware() //DB 커밋이 끝난 뒤에 캐시 삭제
                .build();


    }

    //Redis가 잠까 안될시 : 에러를 내지 않고 로그만 남긴 뒤 DB에서 조회
    @Override
    public CacheErrorHandler errorHandler(){
        return new LoggingCacheErrorHandler();
    }


}
