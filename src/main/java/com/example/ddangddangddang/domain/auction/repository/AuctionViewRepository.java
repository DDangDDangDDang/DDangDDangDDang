package com.example.ddangddangddang.domain.auction.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations.TypedTuple;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * 경매 조회수를 Redis에 둔다. 인기 경매 선별을 위해 사용한다
 *
 * 저장하는 것 두가지
 * - auction:views                          경매별 조회수를 모아 둔 순위표
 * - auction:views:viewer:{경매ID}:{사람}    "회원 2가 5번 경매를 봤음" 표시. 30분 뒤 사라진다.
 *   표시가 남아 있는 동안 또 볼시 조회수를 올리지 않는다(새로고침으로 조작 방지)
 */
@Repository
@RequiredArgsConstructor
public class AuctionViewRepository {

    static final String VIEWS_KEY = "auction:views";
    static final String VIEWER_KEY_PREFIX = "auction:views:viewer:";

    private final StringRedisTemplate redisTemplate;

    /**
     * 처음 본 사람이면 조회수를 1씩 올린다.
     * 같은 사람이 30분 안에 다시 보면 올리지 않는다. (새로고침 반복 방지)
     */
    public boolean increaseIfFirstView(Long auctionId, String viewer, Duration ttl){
        String viewerKey = VIEWER_KEY_PREFIX + auctionId + ":" + viewer;
        //표시가 없을 때만 새로 만든다. 새로 만들었으면 처음 본것이다.
        boolean firstView = Boolean.TRUE.equals(redisTemplate.opsForValue().setIfAbsent(viewerKey, "1",ttl));
        if (firstView){
            redisTemplate.opsForZSet().incrementScore(VIEWS_KEY, String.valueOf(auctionId), 1);
        }
        return firstView;
    }


    // 순위표 전체를 "경매 ID -> 조회수"로 가져온다. 끝난 경매는 지우므로 대기,진행 중 경매만 남게 된다.
    public Map<Long, Long> allViewCounts(){
        Set<TypedTuple<String>> tuples = redisTemplate.opsForZSet().rangeWithScores(VIEWS_KEY, 0, -1);
        if (tuples == null){
            return Map.of();
        }
        HashMap<Long, Long> result = new HashMap<>();
        for (TypedTuple<String> tuple : tuples){
            if (tuple.getValue() != null && tuple.getScore() != null){
                result.put(Long.parseLong(tuple.getValue()),tuple.getScore().longValue());
            }
        }
        return result;

    }

    // 끝난 경매(낙찰,유찰,취소)를 순위표에서 지운다. 끝난 경매가 계속 쌓이지 않게 한다
    public void remove(Long auctionId){
        redisTemplate.opsForZSet().remove(VIEWS_KEY,String.valueOf(auctionId));
    }
}
