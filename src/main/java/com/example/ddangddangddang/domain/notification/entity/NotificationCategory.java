package com.example.ddangddangddang.domain.notification.entity;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.Set;

/**
 * 회원이 알림 설정에서 켜고 끄는 단위. 알림 종류(NotificationType) 여러 개를 하나로 묶는다. (낙찰·유찰·종료는 "경매 결과")
 * 끈 묶음의 알림은 알림함에도 저장하지 않고, 실시간·브라우저 알림도 보내지 않는다.
 */
@Getter
@RequiredArgsConstructor
public enum NotificationCategory {

    LIVE_STARTED("방송 시작", "구독한 판매자가 방송을 시작할 때", Set.of(NotificationType.LIVE_STARTED)),
    AUCTION_STARTED("경매 시작", "알림 받기를 한 경매가 시작될 때", Set.of(NotificationType.AUCTION_STARTED)),
    AUCTION_OUTBID("상위 입찰", "내 입찰보다 높은 입찰이 들어올 때", Set.of(NotificationType.AUCTION_OUTBID)),
    AUCTION_CLOSING_SOON("마감 임박", "알림 받기·입찰한 경매가 10분 안에 끝날 때", Set.of(NotificationType.AUCTION_CLOSING_SOON)),
    AUCTION_CANCELED("경매 취소", "알림 받기·입찰한 경매가 취소될 때", Set.of(NotificationType.AUCTION_CANCELED)),
    AUCTION_RESULT("경매 결과", "낙찰·유찰 등 경매가 끝났을 때 (내 경매의 결과 포함)",
            Set.of(NotificationType.AUCTION_WON, NotificationType.AUCTION_SOLD, NotificationType.AUCTION_FAILED, NotificationType.AUCTION_ENDED)),
    DONATION_POINT("도네이션 마일리지", "도네이션 마일리지가 적립되거나 곧 사라질 때",
            Set.of(NotificationType.DONATION_POINT_EARNED, NotificationType.DONATION_POINT_EXPIRING));

    private final String label;
    private final String description;
    private final Set<NotificationType> types;

    public static NotificationCategory of(NotificationType type) {
        for (NotificationCategory category : values()) {
            if (category.types.contains(type)) {
                return category;
            }
        }
        throw new IllegalArgumentException("알림 묶음이 없는 알림 종류: " + type);
    }
}
