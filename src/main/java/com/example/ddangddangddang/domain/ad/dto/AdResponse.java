package com.example.ddangddangddang.domain.ad.dto;

import com.example.ddangddangddang.domain.ad.entity.Ad;
import com.example.ddangddangddang.domain.ad.entity.AdPlacement;

import java.time.LocalDateTime;
import java.util.function.Function;

/**
 * 광고 한 건. 관리자 목록과 화면 배너가 같이 쓴다.
 * (화면 배너 API는 켜진 광고만 내려서 enabled는 항상 true다)
 */
public record AdResponse(
        Long id,
        AdPlacement placement,
        String imageUrl,
        String linkUrl,
        String altText,
        boolean enabled,
        LocalDateTime createdAt
) {

    /** @param imageUrl 저장소 키를 화면에서 보여 줄 주소로 바꾼다 (FileStorage::publicUrl) */
    public static AdResponse from(Ad ad, Function<String, String> imageUrl) {
        return new AdResponse(ad.getId(), ad.getPlacement(), imageUrl.apply(ad.getImageKey()),
                ad.getLinkUrl(), ad.getAltText(), ad.isEnabled(), ad.getCreatedAt());
    }
}
