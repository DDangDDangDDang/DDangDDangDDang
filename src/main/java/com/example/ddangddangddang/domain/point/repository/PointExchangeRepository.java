package com.example.ddangddangddang.domain.point.repository;

import com.example.ddangddangddang.domain.point.entity.PointExchange;
import com.example.ddangddangddang.domain.point.entity.PointExchangeType;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PointExchangeRepository extends JpaRepository<PointExchange, Long> {

    /**
     * 상태를 바꾸는 모든 경로(취소, 송금 시작, 결과 반영)에서 사용한다.
     * 사용자 취소와 워커의 송금 시작이 동시에 일어나도 둘 중 하나만 성공한다.
     * 서버가 여러 대여도 같은 건을 두 워커가 동시에 송금하지 않는다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select e from PointExchange e where e.id = :id")
    Optional<PointExchange> findByIdForUpdate(@Param("id") Long id);

    /** 내 환전 내역. cursor보다 id가 작은 것을 최신순으로 */
    @Query("""
            select e from PointExchange e
            where e.userId = :userId
              and (:cursor is null or e.id < :cursor)
            order by e.id desc
            """)
    List<PointExchange> findByCursor(@Param("userId") Long userId,
                                     @Param("cursor") Long cursor,
                                     Pageable pageable);

    /** 송금 워커가 처리할 대상 */
    @Query("select e.id from PointExchange e where e.status = :status order by e.id")
    List<Long> findIdsByStatus(@Param("status") PointExchangeType status, Pageable pageable);

    /** 복구 스케줄러가 처리할 대상: PROCESSING에 오래 머문 건 */
    @Query("""
            select e.id from PointExchange e
            where e.status = :status
              and e.processingStartedAt < :before
            order by e.id
            """)
    List<Long> findIdsByStatusAndProcessingStartedAtBefore(@Param("status") PointExchangeType status,
                                                           @Param("before") LocalDateTime before,
                                                           Pageable pageable);
}
