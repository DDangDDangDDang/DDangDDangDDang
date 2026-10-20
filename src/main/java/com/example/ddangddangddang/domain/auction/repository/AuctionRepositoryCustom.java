package com.example.ddangddangddang.domain.auction.repository;

import com.example.ddangddangddang.domain.auction.dto.request.AuctionSearchCondition;
import com.example.ddangddangddang.domain.auction.dto.response.AuctionListResponse;
import com.example.ddangddangddang.domain.auction.entity.Auction;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuctionRepositoryCustom {

    //검색 조건으로 경매 목록을 페이지 단위 조회
    Page<AuctionListResponse> search(AuctionSearchCondition condition, Pageable pageable);

    //인기 경매: 진행 중인 경매 중 입찰 횟수 많은 순으로 조회(입찰이 들어올 시 +1,같은 분이 여러번 입찰을해도 중첩)
    List<AuctionListResponse> findPopular(int limit);

}
