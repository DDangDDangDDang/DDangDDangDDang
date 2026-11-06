package com.example.ddangddangddang.domain.auction.service;


import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

//경매 조회수를 DB에 적는다(AuctionScheduler가 1분마다 부른다)
@Service
@RequiredArgsConstructor
public class AuctionViewCountService {
    private final AuctionRepository auctionRepository;

    /**
     * Redis에 모인 조회수를 DB 총 조회수에 더한다
     * 하나라도 실패하면 전부 되돌린다. 그러면 Redis에서도 빼지 않으므로 다음 실행에서 다시 더해진다.*/
    @Transactional
    public void addViewCounts(Map<Long,Long> addedCounts){
        addedCounts.forEach(auctionRepository::addViewCount);
    }

}
