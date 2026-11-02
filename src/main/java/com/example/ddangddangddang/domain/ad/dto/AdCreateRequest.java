package com.example.ddangddangddang.domain.ad.dto;

import com.example.ddangddangddang.domain.ad.entity.Ad;
import com.example.ddangddangddang.domain.ad.entity.AdPlacement;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** 광고 등록. imageKey는 업로드 URL로 이미지를 올린 뒤 받은 키 */
public record AdCreateRequest(
        @NotNull(message = "광고 자리는 필수입니다.")
        AdPlacement placement,

        @NotBlank(message = "광고 이미지는 필수입니다.")
        String imageKey,

        @NotBlank(message = "광고 링크는 필수입니다.")
        @Size(max = Ad.MAX_LINK_LENGTH, message = "광고 링크는 500자 이하로 입력해 주세요.")
        String linkUrl,

        @NotBlank(message = "광고 문구는 필수입니다.")
        @Size(max = Ad.MAX_ALT_LENGTH, message = "광고 문구는 100자 이하로 입력해 주세요.")
        String altText
) {
}
