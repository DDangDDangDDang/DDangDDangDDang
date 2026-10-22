import type { NotificationMessage } from '@/api';

/*
 * 알림 한 건의 화면 표시. 문구(title·body)와 갈 곳(link)은 서버가 정해 주고(브라우저 알림과 같은 문구),
 * 화면은 그것이 없을 때의 대신할 값과 팝업 버튼 문구만 정한다.
 */

/** 누르면 갈 화면. 서버가 link를 주지 않은 예전 알림이면 방송·경매 번호로 만든다. */
export function notificationLink(notification: NotificationMessage): string | undefined {
  if (notification.link) return notification.link;
  if (notification.liveStreamId) return `/lives/${notification.liveStreamId}`;
  if (notification.auctionId) return `/auctions/${notification.auctionId}`;
  return undefined;
}

export function notificationTitle(notification: NotificationMessage): string {
  if (notification.title) return notification.title;
  if (notification.type === 'LIVE_STARTED') return `${notification.sellerNickname || '구독한 판매자'}님이 방송을 시작했어요`;
  return '새 알림이 있어요';
}

/** 화면 오른쪽 위 팝업의 버튼 문구 */
export function notificationActionLabel(notification: NotificationMessage): string {
  switch (notification.type) {
    case 'LIVE_STARTED':
      return '보러 가기';
    case 'AUCTION_STARTED':
    case 'AUCTION_OUTBID':
    case 'AUCTION_CLOSING_SOON':
      return '입찰하러 가기';
    default:
      return '경매 보기';
  }
}
