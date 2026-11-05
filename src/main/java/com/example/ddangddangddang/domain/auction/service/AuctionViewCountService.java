package com.example.ddangddangddang.domain.auction.service;


import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.repository.AuctionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

//경매 조회수를 DB에 적는다(AuctionScheduler가 1분마다 부른다)
@Service
@RequiredArgsConstructor
public class AuctionViewCountService {

    // 끝나지 않은 경매 상태(대기, 진행중). 이 둘 이외에 끝난 경매(낙찰,유찰,취소)
    private static final List<AuctionStatus> OPEN_STATUSES = List.of(AuctionStatus.READY, AuctionStatus.ONGOING);

    private final AuctionRepository auctionRepository;

    /**
     * Redis 조회수를 DB에 적고, 그중 끝난 경매의 ID를 돌려준다
     * +1씩 더하지 않고 값을 그대로 덮어써서, 몇번을 실행해도 결과가 같다
     * 그래서 한번 실패해도 다음 실행에서 맞는 값으로 다시 적힌다.*/
    @Transactional
    public List<Long> writeViewCounts(Map<Long,Long> viewCounts){
        viewCounts.forEach(auctionRepository::updateViewCount);
        return auctionRepository.findClosedIds(viewCounts.keySet(), OPEN_STATUSES);
    }

}
