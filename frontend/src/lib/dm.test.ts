import { describe, expect, it } from 'vitest';
import { flattenMessages, isSendableDm, isUnreadByOther, lastMessageId, newClientMessageId, prependMessage, startsNewDay } from './dm';

const message = (messageId: number, senderId = 1, sentAt = '2026-10-21T10:00:00') => ({ messageId, roomId: 1, senderId, content: `메시지 ${messageId}`, sentAt });

describe('flattenMessages', () => {
  it('최신순 페이지를 오래된 것부터 펴고 중복을 뺀다', () => {
    expect(flattenMessages([[message(5), message(4)], [message(4), message(2)], undefined]).map((m) => m.messageId)).toEqual([2, 4, 5]);
  });
});

describe('prependMessage', () => {
  it('새 메시지를 맨 앞에, 이미 있으면 그대로', () => {
    const page = [message(2)];
    expect(prependMessage(page, message(3)).map((m) => m.messageId)).toEqual([3, 2]);
    expect(prependMessage(page, message(2))).toBe(page);
  });
});

describe('isUnreadByOther', () => {
  it('내가 보낸 메시지 중 상대가 읽은 위치보다 뒤면 안 읽음', () => {
    expect(isUnreadByOther(message(5, 1), 1, 4)).toBe(true);
    expect(isUnreadByOther(message(4, 1), 1, 4)).toBe(false);
    expect(isUnreadByOther(message(5, 2), 1, 0)).toBe(false);
    expect(isUnreadByOther(message(1, 1), 1, undefined)).toBe(true);
  });
});

describe('lastMessageId', () => {
  it('가장 큰 ID', () => {
    expect(lastMessageId([message(2), message(9), message(4)])).toBe(9);
    expect(lastMessageId([])).toBeUndefined();
  });
});

describe('startsNewDay', () => {
  it('첫 메시지나 날짜가 바뀌면 구분선', () => {
    expect(startsNewDay(undefined, message(1))).toBe(true);
    expect(startsNewDay(message(1, 1, '2026-10-20T23:59:00'), message(2, 1, '2026-10-21T00:01:00'))).toBe(true);
    expect(startsNewDay(message(1), message(2))).toBe(false);
  });
});

describe('isSendableDm', () => {
  it('공백만 있거나 1000자를 넘으면 보낼 수 없다', () => {
    expect(isSendableDm('  ')).toBe(false);
    expect(isSendableDm('안녕하세요')).toBe(true);
    expect(isSendableDm('가'.repeat(1001))).toBe(false);
  });
});

describe('newClientMessageId', () => {
  it('보낼 때마다 다른 ID', () => {
    expect(newClientMessageId()).not.toBe(newClientMessageId());
  });
});
