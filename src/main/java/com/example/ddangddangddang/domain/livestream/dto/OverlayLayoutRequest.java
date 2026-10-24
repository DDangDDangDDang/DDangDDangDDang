package com.example.ddangddangddang.domain.livestream.dto;

import com.example.ddangddangddang.domain.livestream.entity.OverlayLayout;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

/**
 * 판매자가 스튜디오에서 정한 방송 화면 배치. 네 가지 위치를 모두 보낸다. (기본 배치로 되돌릴 때도 기본 위치를 보낸다)
 */
public record OverlayLayoutRequest(
        // 영상 위에 띄운 경매 카드 묶음
        @NotNull(message = "경매 카드 위치를 정해 주세요.") @Valid OverlayPosition featured,
        // 도네이션 선물 안내
        @NotNull(message = "선물 안내 위치를 정해 주세요.") @Valid OverlayPosition donation,
        // 입찰 안내
        @NotNull(message = "입찰 안내 위치를 정해 주세요.") @Valid OverlayPosition bid,
        // 낙찰 안내
        @NotNull(message = "낙찰 안내 위치를 정해 주세요.") @Valid OverlayPosition sold
) {
    public OverlayLayout toEntity() {
        return new OverlayLayout(featured.x(), featured.y(), donation.x(), donation.y(), bid.x(), bid.y(), sold.x(), sold.y());
    }
}
