package com.example.ddangddangddang.domain.livestream.dto;

import com.example.ddangddangddang.domain.livestream.entity.OverlayBox;
import com.example.ddangddangddang.domain.livestream.entity.OverlayLayout;
import java.util.List;

/**
 * 판매자가 정한 방송 화면 배치. 정하지 않은 방송이면 LiveStreamResponse.overlayLayout이 null이고, 화면은 기본 배치로 그린다.
 * 위치의 hidden이 true면 그 UI는 숨긴 것이다. boxes는 판매자가 직접 넣은 텍스트·이미지 박스다. (없으면 빈 목록)
 */
public record OverlayLayoutResponse(
        OverlayPosition featured,
        OverlayPosition donation,
        OverlayPosition bid,
        OverlayPosition sold,
        List<OverlayBox> boxes
) {
    /** 정하지 않았으면(null 또는 빈 칸) null */
    public static OverlayLayoutResponse from(OverlayLayout layout) {
        if (layout == null || layout.getFeaturedX() == null) {
            return null;
        }
        return new OverlayLayoutResponse(
                new OverlayPosition(layout.getFeaturedX(), layout.getFeaturedY(), Boolean.TRUE.equals(layout.getFeaturedHidden())),
                new OverlayPosition(layout.getDonationX(), layout.getDonationY(), Boolean.TRUE.equals(layout.getDonationHidden())),
                new OverlayPosition(layout.getBidX(), layout.getBidY(), Boolean.TRUE.equals(layout.getBidHidden())),
                new OverlayPosition(layout.getSoldX(), layout.getSoldY(), Boolean.TRUE.equals(layout.getSoldHidden())),
                layout.boxesOrEmpty()
        );
    }
}
