package com.example.ddangddangddang.domain.review.dto.response;

import com.example.ddangddangddang.domain.review.entity.Review;

import java.time.LocalDateTime;
//리뷰 하나
public record ReviewResponse (
        Long reviewId,
        Long sellerId,
        String productName,
        int rating,
        String  content,
        LocalDateTime createdAt,
        ReplyResponse reply
){
    public static ReviewResponse from(Review review){
        return new ReviewResponse(
                review.getId(),
                review.getSellerId(),
                review.getProductName(),
                review.getRating(),
                review.getContent(),
                review.getRepliedAt(),
                review.hasReply() ? new ReplyResponse(review.getReplyContent(),review.getRepliedAt()) : null
        );
    }

    public record ReplyResponse(
            String content,
            LocalDateTime repliedAt
    ){

    }
}
