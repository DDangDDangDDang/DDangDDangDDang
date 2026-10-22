package com.example.ddangddangddang.domain.notification.event;

import com.example.ddangddangddang.domain.auction.entity.AuctionStatus;
import com.example.ddangddangddang.domain.auction.event.AuctionEndedEvent;
import com.example.ddangddangddang.domain.notification.entity.Notification;
import com.example.ddangddangddang.domain.notification.entity.NotificationContent;
import com.example.ddangddangddang.domain.notification.entity.NotificationType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

class AuctionNotifierTest {

    private static final Long AUCTION = 7L;
    private static final Long SELLER = 1L;
    private static final Long WINNER = 2L;

    @Test
    @DisplayName("낙찰: 낙찰자는 낙찰, 판매자는 낙찰됨, 나머지 알림 받기·입찰자는 종료 (판매자·낙찰자에게 종료를 또 보내지 않음)")
    void sold() {
        AuctionEndedEvent event = new AuctionEndedEvent(AUCTION, AuctionStatus.ENDED, SELLER, WINNER, 25_000L, "필름 카메라");

        List<Notification> result = AuctionNotifier.endedNotifications(event, List.of(WINNER, 3L, SELLER, 4L));

        assertThat(result).extracting(Notification::getUserId, Notification::getType, Notification::getPrice).containsExactly(
                tuple(WINNER, NotificationType.AUCTION_WON, 25_000L),
                tuple(SELLER, NotificationType.AUCTION_SOLD, 25_000L),
                tuple(3L, NotificationType.AUCTION_ENDED, null),
                tuple(4L, NotificationType.AUCTION_ENDED, null));
        assertThat(NotificationContent.of(result.get(0)).body()).isEqualTo("‘필름 카메라’을(를) 25,000P에 낙찰받았어요. 주문 내역에서 확인하세요.");
        assertThat(NotificationContent.of(result.get(0)).link()).isEqualTo("/auctions/7");
    }

    @Test
    @DisplayName("유찰: 판매자는 유찰, 알림 받기를 한 회원은 종료")
    void failed() {
        AuctionEndedEvent event = new AuctionEndedEvent(AUCTION, AuctionStatus.FAILED, SELLER, null, null, "필름 카메라");

        List<Notification> result = AuctionNotifier.endedNotifications(event, List.of(3L));

        assertThat(result).extracting(Notification::getUserId, Notification::getType).containsExactly(
                tuple(SELLER, NotificationType.AUCTION_FAILED),
                tuple(3L, NotificationType.AUCTION_ENDED));
    }

    @Test
    @DisplayName("상위 입찰 알림은 새 현재가를 보여 준다")
    void outbidContent() {
        Notification notification = Notification.auction(3L, NotificationType.AUCTION_OUTBID, AUCTION, "필름 카메라", 1_200_000L);

        NotificationContent content = NotificationContent.of(notification);

        assertThat(content.title()).isEqualTo("더 높은 입찰이 들어왔어요");
        assertThat(content.body()).isEqualTo("‘필름 카메라’ 현재가가 1,200,000P가 됐어요.");
    }
}
