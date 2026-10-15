package com.example.ddangddangddang.domain.livestream.repository;

import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface LiveStreamRepository extends JpaRepository<LiveStream, Long> {

    boolean existsBySellerIdAndStatus(Long sellerId, LiveStreamStatus status);

    boolean existsByIdAndStatus(Long id, LiveStreamStatus status);

    // 판매자의 종료되지 않은 방송 (대기 중·방송 중). 판매자당 하나만 있다.
    Optional<LiveStream> findByActiveSellerId(Long sellerId);

    boolean existsByActiveSellerId(Long sellerId);
}
