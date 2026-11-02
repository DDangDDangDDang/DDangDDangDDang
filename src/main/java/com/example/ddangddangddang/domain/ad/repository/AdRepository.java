package com.example.ddangddangddang.domain.ad.repository;

import com.example.ddangddangddang.domain.ad.entity.Ad;
import com.example.ddangddangddang.domain.ad.entity.AdPlacement;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AdRepository extends JpaRepository<Ad, Long> {

    /** 화면에 보여 줄 광고: 이 자리의 켜진 광고, 먼저 등록한 순 */
    List<Ad> findByPlacementAndEnabledTrueOrderByIdAsc(AdPlacement placement);

    /** 관리자 목록: 이 자리의 모든 광고, 먼저 등록한 순 */
    List<Ad> findByPlacementOrderByIdAsc(AdPlacement placement);

    /** 관리자 목록: 모든 광고, 자리별·먼저 등록한 순 */
    List<Ad> findAllByOrderByPlacementAscIdAsc();
}
