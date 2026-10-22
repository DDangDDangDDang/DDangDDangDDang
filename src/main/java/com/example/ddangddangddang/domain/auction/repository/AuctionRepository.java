package com.example.ddangddangddang.domain.auction.repository;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;

public interface AuctionRepository extends JpaRepository<Auction,Long>,AuctionRepositoryCustom {

    //해당 상품에 상태(대기,진행 중)의 경매가 있는 조회
    boolean existsByProductIdAndStatusIn(Long productId, List<AuctionStatus> statuses);

    //경매에 올린 상품 정보 조회
    List<Auction> findByProductIdInOrderByIdDesc(List<Long> productIds);

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

    //판매자 프로필 경매 기록 : 판매자의 모든 경매(취소 포함) 최신순. cursor는 이전 페이지 마지막 경매 ID
    @EntityGraph(attributePaths = {"product", "liveStream"})
    @Query("select a from Auction a where a.product.seller.id = :sellerId and (:cursor is null or a.id < :cursor) order by a.id desc")
    List<Auction> findSellerHistory(@Param("sellerId") Long sellerId, @Param("cursor") Long cursor, Pageable pageable);

    //판매자 프로필 신뢰 지표 : 판매자의 경매 중 그 상태인 것의 수(낙찰·유찰·취소)
    long countByProductSellerIdAndStatus(Long sellerId, AuctionStatus status);
}
