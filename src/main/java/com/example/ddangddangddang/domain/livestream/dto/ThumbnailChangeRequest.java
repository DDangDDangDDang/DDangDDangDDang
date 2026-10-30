package com.example.ddangddangddang.domain.livestream.dto;

import jakarta.validation.constraints.NotBlank;

/** 올린 썸네일로 방송 카드 썸네일을 바꾼다. (POST /thumbnail-uploads에서 받은 키) */
public record ThumbnailChangeRequest(

        @NotBlank(message = "썸네일 키는 필수입니다.")
        String thumbnailKey
) {
}
