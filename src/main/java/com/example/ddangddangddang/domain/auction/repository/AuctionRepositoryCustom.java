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

    //검색 조건으로 경매 목록을 페이지 단위 죄
    Page<AuctionListResponse> search(AuctionSearchCondition condition, Pageable pageable);

}
