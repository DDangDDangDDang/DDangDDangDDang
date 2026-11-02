package com.example.ddangddangddang.domain.ad.entity;

import com.example.ddangddangddang.global.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 배너 광고. 관리자가 자리별로 등록하고 켜고 끈다.
 * 한 자리에 켜진 광고가 2개 이상이면 화면에서 6초마다 번갈아 보여 준다. (AdBanner)
 */
@Getter
@Entity
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Table(name = "ads", indexes = @Index(name = "idx_ads_placement_enabled", columnList = "placement, enabled"))
public class Ad extends BaseTimeEntity {

    public static final int MAX_LINK_LENGTH = 500;
    public static final int MAX_ALT_LENGTH = 100;

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AdPlacement placement;

    /** 저장소 키 (ads/uuid.확장자). 화면 주소는 FileStorage.publicUrl로 만든다 */
    @Column(nullable = false, length = 200)
    private String imageKey;

    /** 누르면 새 탭으로 여는 광고주 주소. http·https만 받는다 */
    @Column(nullable = false, length = MAX_LINK_LENGTH)
    private String linkUrl;

    /** 이미지 대신 읽어 줄 광고 문구 */
    @Column(nullable = false, length = MAX_ALT_LENGTH)
    private String altText;

    /** 켜진 광고만 화면에 나온다 */
    @Column(nullable = false)
    private boolean enabled;

    private Ad(AdPlacement placement, String imageKey, String linkUrl, String altText) {
        this.placement = placement;
        this.imageKey = imageKey;
        this.linkUrl = linkUrl;
        this.altText = altText;
        this.enabled = true;
    }

    /** 새 광고는 켜진 상태로 만든다 */
    public static Ad create(AdPlacement placement, String imageKey, String linkUrl, String altText) {
        return new Ad(placement, imageKey, linkUrl, altText);
    }

    public void changeEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
