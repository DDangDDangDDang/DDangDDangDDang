package com.example.ddangddangddang.domain.auction.dto.request;

import com.example.ddangddangddang.domain.auction.entity.AuctionSort;
import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;

public record AuctionSearchCondition(
        String productName, //상품명(없으면 전체)
        Long categoryId, //카테고리
        Long minPrice, //최소가
        Long maxPrice, //최대가
        AuctionStatus status, //경매 상태값
        AuctionSort sort //없을시 LATEST(최신)
) {
}
