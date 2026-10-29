package com.example.ddangddangddang.domain.livestream.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Embeddable;
import java.util.List;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매자가 정한 방송 화면 배치. 영상 위에 그리는 것마다 가로(x)·세로(y) 위치를 0~100(%)으로 둔다.
 * 0이면 왼쪽(위쪽) 끝, 100이면 오른쪽(아래쪽) 끝, 50이면 가운데에 붙는다. 화면 크기가 달라도 영상 밖으로 나가지 않는다.
 * - 경매 카드·선물·입찰·낙찰 안내는 판매자가 숨길 수 있다. (…Hidden이 true면 시청 화면에 그리지 않는다. null은 보임)
 * - 판매자가 직접 넣은 텍스트·이미지 박스는 boxes에 둔다. (OverlayBox, 한 칸에 JSON으로)
 * 정하지 않은 방송은 모든 칸이 null이고(엔티티에서는 null로 읽힘), 화면은 기본 배치로 그린다.
 */
@Embeddable
@Getter
@EqualsAndHashCode
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class OverlayLayout {

    // 영상 위에 띄운 경매 카드 (최대 2장이 위아래로 쌓인 묶음)
    @Column(name = "overlay_featured_x")
    private Integer featuredX;

    @Column(name = "overlay_featured_y")
    private Integer featuredY;

    @Column(name = "overlay_featured_hidden")
    private Boolean featuredHidden;

    // 도네이션 선물 안내
    @Column(name = "overlay_donation_x")
    private Integer donationX;

    @Column(name = "overlay_donation_y")
    private Integer donationY;

    @Column(name = "overlay_donation_hidden")
    private Boolean donationHidden;

    // 입찰 안내
    @Column(name = "overlay_bid_x")
    private Integer bidX;

    @Column(name = "overlay_bid_y")
    private Integer bidY;

    @Column(name = "overlay_bid_hidden")
    private Boolean bidHidden;

    // 낙찰 안내
    @Column(name = "overlay_sold_x")
    private Integer soldX;

    @Column(name = "overlay_sold_y")
    private Integer soldY;

    @Column(name = "overlay_sold_hidden")
    private Boolean soldHidden;

    // 판매자가 직접 넣은 텍스트·이미지 박스. 없으면 null로 저장하고 빈 목록으로 읽는다.
    @Convert(converter = OverlayBoxesConverter.class)
    @Column(name = "overlay_boxes", columnDefinition = "text")
    private List<OverlayBox> boxes;

    /** 박스 목록 (없으면 빈 목록) */
    public List<OverlayBox> boxesOrEmpty() {
        return boxes == null ? List.of() : boxes;
    }
}
