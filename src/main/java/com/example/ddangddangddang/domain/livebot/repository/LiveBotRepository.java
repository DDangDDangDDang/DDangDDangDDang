package com.example.ddangddangddang.domain.livebot.repository;

import com.example.ddangddangddang.domain.livebot.entity.LiveBot;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LiveBotRepository extends JpaRepository<LiveBot, Long> {

    /** 관리자 목록: 최근에 만든 봇부터 */
    List<LiveBot> findAllByOrderByIdDesc();

    /** 이 회원이 라이브 봇 계정인지 */
    boolean existsByUserId(Long userId);
}
