package com.example.ddangddangddang.domain.livestream.dto;

import com.example.ddangddangddang.domain.livestream.entity.OverlayBox;
import com.example.ddangddangddang.domain.livestream.entity.OverlayLayout;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/**
 * 판매자가 스튜디오에서 정한 방송 화면 배치. 네 가지 위치를 모두 보낸다. (기본 배치로 되돌릴 때도 기본 위치를 보낸다)
 * 숨긴 UI는 위치의 hidden을 true로 보낸다. 직접 넣은 텍스트·이미지 박스는 boxes에 모두 보낸다. (보내지 않으면 박스 없음)
 */
public record OverlayLayoutRequest(
        // 영상 위에 띄운 경매 카드 묶음
        @NotNull(message = "경매 카드 위치를 정해 주세요.") @Valid OverlayPosition featured,
        // 도네이션 선물 안내
        @NotNull(message = "선물 안내 위치를 정해 주세요.") @Valid OverlayPosition donation,
        // 입찰 안내
        @NotNull(message = "입찰 안내 위치를 정해 주세요.") @Valid OverlayPosition bid,
        // 낙찰 안내
        @NotNull(message = "낙찰 안내 위치를 정해 주세요.") @Valid OverlayPosition sold,
        // 판매자가 직접 넣은 박스 (화면 위에서 뒤에 있는 것이 위에 그려진다)
        @Size(max = MAX_BOXES, message = "텍스트·이미지 박스는 10개까지 넣을 수 있습니다.") List<@Valid @NotNull OverlayBoxRequest> boxes
) {
    public static final int MAX_BOXES = 10;

    public OverlayLayoutRequest(OverlayPosition featured, OverlayPosition donation, OverlayPosition bid, OverlayPosition sold) {
        this(featured, donation, bid, sold, List.of());
    }

    public List<OverlayBoxRequest> boxesOrEmpty() {
        return boxes == null ? List.of() : boxes;
    }

    /** 박스는 서비스에서 검사·정리한 것(boxes)을 넣는다. */
    public OverlayLayout toEntity(List<OverlayBox> boxes) {
        return new OverlayLayout(
                featured.x(), featured.y(), featured.hiddenOrFalse(),
                donation.x(), donation.y(), donation.hiddenOrFalse(),
                bid.x(), bid.y(), bid.hiddenOrFalse(),
                sold.x(), sold.y(), sold.hiddenOrFalse(),
                boxes.isEmpty() ? null : List.copyOf(boxes));
    }

    /** 박스가 없는 배치 */
    public OverlayLayout toEntity() {
        return toEntity(List.of());
    }
}
