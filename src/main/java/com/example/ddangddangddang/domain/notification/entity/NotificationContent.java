package com.example.ddangddangddang.domain.notification.entity;

import java.text.NumberFormat;
import java.util.Locale;

/**
 * 알림의 화면 문구와 누르면 갈 곳. 알림함·실시간 팝업·브라우저 알림(웹 푸시)이 같은 문구를 쓴다.
 *
 * @param title 한 줄 제목
 * @param body  설명 (방송 제목·상품명 등)
 * @param link  누르면 갈 화면 경로 (프론트 라우트)
 */
public record NotificationContent(String title, String body, String link) {

    public static NotificationContent of(Notification notification) {
        String product = notification.getProductName() == null ? "경매 상품" : "‘" + notification.getProductName() + "’";
        String auctionLink = "/auctions/" + notification.getAuctionId();
        return switch (notification.getType()) {
            case LIVE_STARTED -> new NotificationContent(
                    (notification.getSellerNickname() == null ? "구독한 판매자" : notification.getSellerNickname())
                            + (notification.isLiveRecorded() ? "님이 녹화 영상 방송을 시작했어요" : "님이 방송을 시작했어요"),
                    notification.getLiveTitle(), "/lives/" + notification.getLiveStreamId());
            case AUCTION_STARTED -> new NotificationContent("경매가 시작됐어요", product + " 경매에 지금 입찰할 수 있어요.", auctionLink);
            case AUCTION_OUTBID -> new NotificationContent("더 높은 입찰이 들어왔어요",
                    product + " 현재가가 " + point(notification.getPrice()) + "가 됐어요.", auctionLink);
            case AUCTION_CLOSING_SOON -> new NotificationContent("경매가 곧 마감돼요", product + " 경매가 10분 안에 끝나요.", auctionLink);
            case AUCTION_CANCELED -> new NotificationContent("경매가 취소됐어요", product + " 경매를 판매자가 취소했어요.", auctionLink);
            case AUCTION_WON -> new NotificationContent("낙찰받았어요",
                    product + "을(를) " + point(notification.getPrice()) + "에 낙찰받았어요. 주문 내역에서 확인하세요.", auctionLink);
            case AUCTION_SOLD -> new NotificationContent("경매가 낙찰됐어요",
                    product + "이(가) " + point(notification.getPrice()) + "에 낙찰됐어요. 주문 내역에서 발송해 주세요.", auctionLink);
            case AUCTION_FAILED -> new NotificationContent("경매가 유찰됐어요", product + " 경매가 입찰 없이 끝났어요.", auctionLink);
            case AUCTION_ENDED -> new NotificationContent("경매가 끝났어요", product + " 경매가 마감됐어요.", auctionLink);
            // 도네이션 포인트는 방송 중인 판매자에게 선물하므로 라이브 목록으로 보낸다.
            case DONATION_POINT_EARNED -> new NotificationContent("도네이션 포인트 " + point(notification.getPrice()) + "가 적립됐어요",
                    "구매 확정으로 적립했어요. 응원하는 판매자의 라이브 방송에서 선물해 보세요.", "/lives");
            case DONATION_POINT_EXPIRING -> new NotificationContent("도네이션 포인트 " + point(notification.getPrice()) + "가 곧 사라져요",
                    "유효기간이 얼마 남지 않았어요. 사라지기 전에 응원하는 판매자에게 선물해 보세요.", "/lives");
        };
    }

    private static String point(Long price) {
        return price == null ? "-" : NumberFormat.getNumberInstance(Locale.KOREA).format(price) + "P";
    }
}
