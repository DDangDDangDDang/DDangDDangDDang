package com.example.ddangddangddang.domain.livestream.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 판매자가 정한 방송 화면 배치. 영상 위에 그리는 것마다 가로(x)·세로(y) 위치를 0~100(%)으로 둔다.
 * 0이면 왼쪽(위쪽) 끝, 100이면 오른쪽(아래쪽) 끝, 50이면 가운데에 붙는다. 화면 크기가 달라도 영상 밖으로 나가지 않는다.
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

    // 도네이션 선물 안내
    @Column(name = "overlay_donation_x")
    private Integer donationX;

    @Column(name = "overlay_donation_y")
    private Integer donationY;

    // 입찰 안내
    @Column(name = "overlay_bid_x")
    private Integer bidX;

    @Column(name = "overlay_bid_y")
    private Integer bidY;

    // 낙찰 안내
    @Column(name = "overlay_sold_x")
    private Integer soldX;

    @Column(name = "overlay_sold_y")
    private Integer soldY;
}
