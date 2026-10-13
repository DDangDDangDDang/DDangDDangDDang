package com.example.ddangddangddang.domain.livestream.repository;

import com.example.ddangddangddang.domain.livestream.entity.LiveStream;
import com.example.ddangddangddang.domain.livestream.entity.LiveStreamStatus;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LiveStreamRepository extends JpaRepository<LiveStream, Long> {

    boolean existsBySellerIdAndStatus(Long sellerId, LiveStreamStatus status);
}
