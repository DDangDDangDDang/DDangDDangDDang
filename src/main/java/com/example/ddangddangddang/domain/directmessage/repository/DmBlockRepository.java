package com.example.ddangddangddang.domain.directmessage.repository;

import com.example.ddangddangddang.domain.directmessage.entity.DmBlock;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface DmBlockRepository extends JpaRepository<DmBlock, Long> {

    boolean existsByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    Optional<DmBlock> findByBlockerIdAndBlockedId(Long blockerId, Long blockedId);

    List<DmBlock> findByBlockerIdOrderByIdDesc(Long blockerId);

    // 대화방 목록에서 "내가 차단한 상대"를 한 번에 표시하기 위해 쓴다.
    @Query("select b.blockedId from DmBlock b where b.blockerId = :blockerId and b.blockedId in :userIds")
    List<Long> findBlockedIds(@Param("blockerId") Long blockerId, @Param("userIds") Collection<Long> userIds);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from DmBlock b where b.blockerId = :blockerId and b.blockedId = :blockedId")
    int deleteBlock(@Param("blockerId") Long blockerId, @Param("blockedId") Long blockedId);
}
