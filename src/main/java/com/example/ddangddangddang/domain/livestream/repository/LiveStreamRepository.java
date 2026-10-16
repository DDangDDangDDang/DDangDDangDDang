package com.example.ddangddangddang.domain.livestream.repository;

import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface LiveStreamRepository extends JpaRepository<LiveStream, Long> {

    boolean existsBySellerIdAndStatus(Long sellerId, LiveStreamStatus status);

    boolean existsByIdAndStatus(Long id, LiveStreamStatus status);

    // 판매자의 종료되지 않은 방송 (대기 중·방송 중). 판매자당 하나만 있다.
    Optional<LiveStream> findByActiveSellerId(Long sellerId);

    boolean existsByActiveSellerId(Long sellerId);

    // 시청자 수 집계 대상 (진행 중인 방송 전체)
    List<LiveStream> findAllByStatus(LiveStreamStatus status);

    // 홈 화면에서 시청자 수 정보가 아직 없을 때 채우는 용도. 최근 시작한 방송부터
    List<LiveStream> findByStatusOrderByStartedAtDesc(LiveStreamStatus status, Pageable pageable);
}
