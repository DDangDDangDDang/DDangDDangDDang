import { describe, expect, it } from 'vitest';
import { notificationActionLabel, notificationLink, notificationTitle } from './notification-view';

describe('notification-view', () => {
  it('서버가 준 문구와 경로를 그대로 쓴다', () => {
    const outbid = { type: 'AUCTION_OUTBID', auctionId: 7, title: '더 높은 입찰이 들어왔어요', link: '/auctions/7' } as const;
    expect(notificationTitle(outbid)).toBe('더 높은 입찰이 들어왔어요');
    expect(notificationLink(outbid)).toBe('/auctions/7');
    expect(notificationActionLabel(outbid)).toBe('입찰하러 가기');
  });

  it('문구·경로가 없는 예전 알림은 방송·경매 번호로 대신한다', () => {
    const live = { type: 'LIVE_STARTED', sellerNickname: '필름상점', liveStreamId: 3 } as const;
    expect(notificationTitle(live)).toBe('필름상점님이 방송을 시작했어요');
    expect(notificationLink(live)).toBe('/lives/3');
    expect(notificationActionLabel(live)).toBe('보러 가기');
    expect(notificationLink({ type: 'AUCTION_WON', auctionId: 9 })).toBe('/auctions/9');
    expect(notificationActionLabel({ type: 'AUCTION_WON' })).toBe('경매 보기');
  });
});
