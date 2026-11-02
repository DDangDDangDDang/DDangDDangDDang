package com.example.ddangddangddang.domain.review.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
//판매자 답글
public record ReviewReplyRequest(
        @NotBlank(message = "답글 내용은 필수입니다")
        @Size(max = 300, message = "답글은 300자 이하로 입력해주세요")
        String content
) {
}
