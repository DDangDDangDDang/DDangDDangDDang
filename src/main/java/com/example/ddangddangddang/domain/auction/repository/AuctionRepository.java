package com.example.ddangddangddang.domain.auction.repository;

import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AuctionRepository extends JpaRepository<Auction,Long>,AuctionRepositoryCustom {

    //해당 상품에 상태(대기,진행 중)의 경매가 있는 조회
    boolean existsByProductIdAndStatusIn(Long productId, List<AuctionStatus> statuses);


    //시작 시간이 지난 READY 경매(READY -> ONGOING 대상)
    List<Auction> findByStatusAndStartAtLessThanEqual(AuctionStatus status,LocalDateTime now);

    //종료 시간이 지난 ONGOING 경매(ONGOING -> ENDED/FAILED 대상)
    List<Auction> findByStatusAndEndAtLessThanEqual(AuctionStatus status, LocalDateTime now);

    /**
     * 경매 행을 비관적 락(쓰기 잠금)으로 조회한다. (동시성 제어용, 현재 미사용)
     *
     * 입찰처럼 같은 경매를 여러 요청이 동시에 바꾸는 기능이 생기면 이 메서드로 조회해서 순서대로 처리한다.
     * 락 순서 규칙: 경매와 지갑을 함께 잠글 때는 항상 "경매 → 지갑" 순서로 잠근다.
     * (지갑끼리는 PointService가 userId 작은 순으로 잠근다)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Auction a where a.id = :id")
    Optional<Auction> findByIdForUpdate(@Param("id") Long id);
}
