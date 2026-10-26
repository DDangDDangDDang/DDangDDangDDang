package com.example.ddangddangddang.domain.auction.repository;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AuctionRepository extends JpaRepository<Auction,Long>,AuctionRepositoryCustom {

    //해당 상품에 상태(대기,진행 중)의 경매가 있는 조회
    boolean existsByProductIdAndStatusIn(Long productId, List<AuctionStatus> statuses);

    //상품별 최신 경매 id(상품 마다 가장 큰 id 조회) : 내 상품 목록에서 상품별 경매 상태 표시용
    @Query("select max(a.id) from Auction a where a.product.id in :productIds group by a.product.id")
    List<Long> findLatestIdsByProductIds(@Param("productIds")List<Long> productIds);

    //시작 시간이 지난 READY 경매(READY -> ONGOING 대상)
    List<Auction> findByStatusAndStartAtLessThanEqual(AuctionStatus status,LocalDateTime now);

    //종료 시간이 지난 ONGOING 경매(ONGOING -> ENDED/FAILED 대상)
    List<Auction> findByStatusAndEndAtLessThanEqual(AuctionStatus status, LocalDateTime now);

    /**
     * 스케줄러가 마감할 경매: 종료 시간이 지난 ONGOING 중, 마감 재시도를 기다리는 중이 아니고 재시도 한도에 닿지 않은 것.
     * 마감에 실패한 경매가 1초마다 같은 실패를 반복하지 않게 한다. (AuctionCloseRetryService)
     */
    @Query("""
            select a from Auction a
            where a.status = :status
              and a.endAt <= :now
              and a.closeFailCount < :maxFailures
              and (a.nextCloseAttemptAt is null or a.nextCloseAttemptAt <= :now)
            """)
    List<Auction> findCloseTargets(@Param("status") AuctionStatus status,
                                   @Param("now") LocalDateTime now,
                                   @Param("maxFailures") int maxFailures);

    //마감 임박 알림: 종료 시각이 [from, to] 사이인 진행 중 경매. 알림 문구에 쓰는 상품을 같이 읽는다.
    @EntityGraph(attributePaths = {"product"})
    List<Auction> findByStatusAndEndAtBetween(AuctionStatus status, LocalDateTime from, LocalDateTime to);

    //이 방송에 올라간 경매(최신순 50개). 응답에 쓰는 상품·판매자를 같이 읽는다.
    @EntityGraph(attributePaths = {"product", "product.seller", "liveStream"})
    List<Auction> findTop50ByLiveStreamIdOrderByIdDesc(Long liveStreamId);

    //이 방송에 올라간 경매 중 주어진 상태인지(방송 영상 위에 띄울 경매 확인)
    boolean existsByIdAndLiveStreamIdAndStatusIn(Long id, Long liveStreamId, List<AuctionStatus> statuses);

    //판매자의 경매 중 주어진 상태인 것(최신순 50개)
    @EntityGraph(attributePaths = {"product", "product.seller", "liveStream"})
    List<Auction> findTop50ByProductSellerIdAndStatusInOrderByIdDesc(Long sellerId, List<AuctionStatus> statuses);

    // ===== 입찰 · 즉시구매 =====

    /**
     * 즉시구매처럼 드물고 로직이 긴 작업에서 사용한다.
     * 락 순서 규칙: 항상 "경매 → 지갑" 순서로 잠근다. (입찰도 경매 행 UPDATE → 지갑 순서)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Auction a where a.id = :id")
    Optional<Auction> findByIdForUpdate(@Param("id") Long id);

    /**
     * 입찰 반영 (조건부 UPDATE).
     * 내가 조회했던 version 그대로일 때만 바뀐다. 그 사이 다른 입찰, 취소, 마감이 있었으면 0을 돌려준다.
     * 1이 돌아오면 조회했던 최고 입찰자와 현재가가 "내가 밀어낸 사람과 금액"이라는 게 보장된다.
     *
     * version을 직접 올리는 이유: JPQL UPDATE는 @Version을 자동으로 올리지 않는다.
     * 올리지 않으면 엔티티를 조회해 저장하는 다른 코드(경매 취소 등)가 입찰 결과를 예전 값으로 덮어쓴다.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            update Auction a
               set a.currentPrice = :amount,
                   a.highestBidderId = :bidderId,
                   a.bidCount = a.bidCount + 1,
                   a.endAt = :newEndAt,
                   a.extendCount = a.extendCount + :extendIncrement,
                   a.version = a.version + 1
             where a.id = :auctionId
               and a.version = :seenVersion
               and a.status = :ongoing
               and a.endAt > :now
            """)
    int placeBid(@Param("auctionId") Long auctionId,
                 @Param("bidderId") Long bidderId,
                 @Param("amount") long amount,
                 @Param("newEndAt") LocalDateTime newEndAt,
                 @Param("extendIncrement") int extendIncrement,
                 @Param("seenVersion") Long seenVersion,
                 @Param("ongoing") AuctionStatus ongoing,
                 @Param("now") LocalDateTime now);
    //판매자 프로필 경매 기록 : 판매자의 모든 경매(취소 포함) 최신순. cursor는 이전 페이지 마지막 경매 ID
    @EntityGraph(attributePaths = {"product", "liveStream"})
    @Query("select a from Auction a where a.product.seller.id = :sellerId and (:cursor is null or a.id < :cursor) order by a.id desc")
    List<Auction> findSellerHistory(@Param("sellerId") Long sellerId, @Param("cursor") Long cursor, Pageable pageable);

    //판매자 프로필 신뢰 지표 : 판매자의 경매 중 그 상태인 것의 수(낙찰·유찰·취소)
    long countByProductSellerIdAndStatus(Long sellerId, AuctionStatus status);

    //상점 경매 기록(탭별) : 판매자의 경매 중 탭에 해당하는 상태만 최신순 정렬,cursor는 이전 페이지 마지막 경매 ID
    @EntityGraph(attributePaths = {"product","liveStream"})
    @Query("select a from Auction  a where  a.product.seller.id = :sellerId and a.status in :statuses and (:cursor is null or a.id <:cursor) order by a.id desc")
    List<Auction> findSellerHistoryByStatuses(@Param("sellerId") Long sellerId, @Param("statuses") List<AuctionStatus> statuses,@Param("cursor") Long cursor, Pageable pageable);


    /*상품의 경매 이력(최신순 50) : 현 상품으로 지금까지 열었던 경매 전부(유찰,취소 포함),
    추후 같은 상품 올리시 시작가 정하는데 참고용*/
    @EntityGraph(attributePaths ={"product","product.seller","liveStream"})
    List<Auction> findTop50ByProductIdOrderByIdDesc(Long productId);
}
