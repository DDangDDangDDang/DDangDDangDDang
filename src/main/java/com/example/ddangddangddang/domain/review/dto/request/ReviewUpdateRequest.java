package com.example.ddangddangddang.domain.review.dto.request;

import jakarta.validation.constraints.*;
//구매자 리뷰 수정
public record ReviewUpdateRequest (
    @NotNull(message = "점수는 필수입니다")
    @Min(value = 1,message = "점수는 1점 이상이어야 합니다")
    @Max(value = 5,message = "점수는 5점 이하여야 합니다")
    Integer rating,

    @Size(max = 500,message = "리뷰 내용은 500자 이하 입력이 가능합니다")
    String content
            ){
}
