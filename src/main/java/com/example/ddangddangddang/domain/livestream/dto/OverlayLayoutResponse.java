package com.example.ddangddangddang.domain.livestream.dto;

import com.example.ddangddangddang.domain.livestream.entity.OverlayLayout;

/**
 * 판매자가 정한 방송 화면 배치. 정하지 않은 방송이면 LiveStreamResponse.overlayLayout이 null이고, 화면은 기본 배치로 그린다.
 */
public record OverlayLayoutResponse(
        OverlayPosition featured,
        OverlayPosition donation,
        OverlayPosition bid,
        OverlayPosition sold
) {
    /** 정하지 않았으면(null 또는 빈 칸) null */
    public static OverlayLayoutResponse from(OverlayLayout layout) {
        if (layout == null || layout.getFeaturedX() == null) {
            return null;
        }
        return new OverlayLayoutResponse(
                new OverlayPosition(layout.getFeaturedX(), layout.getFeaturedY()),
                new OverlayPosition(layout.getDonationX(), layout.getDonationY()),
                new OverlayPosition(layout.getBidX(), layout.getBidY()),
                new OverlayPosition(layout.getSoldX(), layout.getSoldY())
        );
    }
}
