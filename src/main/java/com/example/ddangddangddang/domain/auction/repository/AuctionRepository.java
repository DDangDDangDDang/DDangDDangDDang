package com.example.ddangddangddang.domain.auction.repository;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AuctionRepository extends JpaRepository<Auction,Long>,AuctionRepositoryCustom {

    //해당 상품에 상태(대기,진행 중)의 경매가 있는 조회
    boolean existsByProductIdAndStatusIn(Long productId, List<AuctionStatus> statuses);


    //시작 시간이 지난 READY 경매(READY -> ONGOING 대상)
    List<Auction> findByStatusAndStartAtLessThanEqual(AuctionStatus status,LocalDateTime now);

    //종료 시간이 지난 ONGOING 경매(ONGOING -> ENDED/FAILED 대상)
    List<Auction> findByStatusAndEndAtLessThanEqual(AuctionStatus status, LocalDateTime now);

    //이 방송에 올라간 경매(최신순 50개). 응답에 쓰는 상품·판매자를 같이 읽는다.
    @EntityGraph(attributePaths = {"product", "product.seller", "liveStream"})
    List<Auction> findTop50ByLiveStreamIdOrderByIdDesc(Long liveStreamId);

    //판매자의 경매 중 주어진 상태인 것(최신순 50개)
    @EntityGraph(attributePaths = {"product", "product.seller", "liveStream"})
    List<Auction> findTop50ByProductSellerIdAndStatusInOrderByIdDesc(Long sellerId, List<AuctionStatus> statuses);
}
