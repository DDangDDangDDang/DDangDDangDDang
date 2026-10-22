package com.example.ddangddangddang.domain.notification.push;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscription, Long> {

    Optional<PushSubscription> findByEndpointHash(String endpointHash);

    List<PushSubscription> findByUserIdIn(Collection<Long> userIds);

    /** 내 구독만 지운다. (로그아웃·알림 끄기) */
    @Transactional
    @Modifying
    @Query("delete from PushSubscription s where s.userId = :userId and s.endpointHash = :endpointHash")
    int deleteMine(@Param("userId") Long userId, @Param("endpointHash") String endpointHash);

    /** 푸시 서비스가 없어졌다고 답한 구독(404·410)을 지운다. 보내기는 트랜잭션 밖(응답 콜백)에서 하므로 여기서 연다. */
    @Transactional
    @Modifying
    @Query("delete from PushSubscription s where s.endpointHash = :endpointHash")
    int deleteExpired(@Param("endpointHash") String endpointHash);
}
