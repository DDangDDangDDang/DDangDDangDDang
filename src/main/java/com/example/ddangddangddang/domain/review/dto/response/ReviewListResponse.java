package com.example.ddangddangddang.domain.review.dto.response;

import java.util.List;
//리뷰 목록
public record ReviewListResponse (
        List<ReviewResponse> content,
        Long nextCursor,
        boolean hasNext
){
}
