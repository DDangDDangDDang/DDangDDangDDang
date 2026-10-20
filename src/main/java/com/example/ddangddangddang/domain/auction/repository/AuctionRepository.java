package com.example.ddangddangddang.domain.auction.repository;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface AuctionRepository extends JpaRepository<Auction,Long>,AuctionRepositoryCustom {

    //해당 상품에 상태(대기,진행 중)의 경매가 있는 조회
    boolean existsByProductIdAndStatusIn(Long productId, List<AuctionStatus> statuses);

}
