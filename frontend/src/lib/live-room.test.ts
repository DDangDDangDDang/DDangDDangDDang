import { describe, expect, it } from 'vitest';
import type { ChatMessage } from '@/api';
import { appendChatMessage, countViewers, isSendableChat, summarizeParticipants } from './live-room';

const chat = (messageId: string): ChatMessage => ({
  messageId,
  type: 'CHAT',
  liveStreamId: 1,
  senderId: 2,
  nickname: '시청자',
  content: '안녕하세요',
  sentAt: '2026-10-20T20:00:00',
});

describe('summarizeParticipants', () => {
  it('판매자 이름을 찾고, 시청자 수는 나를 포함해 센다', () => {
    const result = summarizeParticipants([
      { identity: 'seller-1', name: '필름상회' },
      { identity: 'viewer-2', name: '사진찍는곰' },
      { identity: 'viewer-3', name: '나무필름' },
    ]);
    expect(result).toEqual({ sellerName: '필름상회', viewerCount: 3 });
  });

  it('다른 참가자가 없으면 나 혼자 1명이다', () => {
    expect(summarizeParticipants([])).toEqual({ sellerName: undefined, viewerCount: 1 });
  });

  it('판매자도 시청자도 아닌 참가자는 세지 않는다', () => {
    expect(summarizeParticipants([{ identity: 'egress-1' }]).viewerCount).toBe(1);
  });
});

describe('countViewers', () => {
  it('시청자만 세고 판매자·그 밖의 참가자는 넣지 않는다', () => {
    expect(countViewers([{ identity: 'viewer-2' }, { identity: 'viewer-3' }, { identity: 'seller-1' }, { identity: 'egress-1' }])).toBe(2);
  });

  it('아무도 없으면 0명이다', () => {
    expect(countViewers([])).toBe(0);
  });
});

describe('appendChatMessage', () => {
  it('끝에 붙인다', () => {
    expect(appendChatMessage([chat('a')], chat('b')).map((m) => m.messageId)).toEqual(['a', 'b']);
  });

  it('같은 메시지는 한 번만 남긴다', () => {
    const list = [chat('a')];
    expect(appendChatMessage(list, chat('a'))).toBe(list);
  });

  it('최대 개수를 넘으면 오래된 것부터 지운다', () => {
    const list = [chat('a'), chat('b'), chat('c')];
    expect(appendChatMessage(list, chat('d'), 3).map((m) => m.messageId)).toEqual(['b', 'c', 'd']);
  });
});

describe('isSendableChat', () => {
  it('공백만 있으면 보낼 수 없다', () => {
    expect(isSendableChat('   ')).toBe(false);
  });

  it('1~200자는 보낼 수 있다', () => {
    expect(isSendableChat('안녕')).toBe(true);
    expect(isSendableChat('가'.repeat(200))).toBe(true);
    expect(isSendableChat('가'.repeat(201))).toBe(false);
  });
});
