import { describe, expect, it } from 'vitest';
import type { ModerationAlert, Schema } from '@/api';
import {
  dateTimeInputOf,
  formValuesOf,
  mediaErrorMessage,
  prependModerationAlert,
  toCreateRequest,
  toUpdateRequest,
  validateStreamForm,
  type StreamFormValues,
} from './studio';

const NOW = new Date('2026-10-21T12:00:00');
const values = (patch: Partial<StreamFormValues> = {}): StreamFormValues => ({ title: '필름 카메라 경매', description: '', scheduledStartAt: '', ...patch });

const scheduled: Schema<'LiveStreamResponse'> = {
  id: 1,
  title: '필름 카메라 경매',
  description: '오늘은 필름 카메라',
  status: 'SCHEDULED',
  scheduledStartAt: '2026-10-21T20:00:00',
};

describe('validateStreamForm', () => {
  it('제목만 있으면 통과한다', () => {
    expect(validateStreamForm(values(), NOW)).toEqual({});
  });

  it('제목이 비었거나 공백뿐이면 오류', () => {
    expect(validateStreamForm(values({ title: '   ' }), NOW).title).toBe('방송 제목을 입력해 주세요.');
  });

  it('제목 100자, 설명 1000자를 넘으면 오류', () => {
    expect(validateStreamForm(values({ title: '가'.repeat(100) }), NOW).title).toBeUndefined();
    expect(validateStreamForm(values({ title: '가'.repeat(101) }), NOW).title).toBeDefined();
    expect(validateStreamForm(values({ description: '가'.repeat(1001) }), NOW).description).toBeDefined();
  });

  it('시작 예정 시간은 지금 이후, 30일 이내', () => {
    expect(validateStreamForm(values({ scheduledStartAt: '2026-10-21T11:59' }), NOW).scheduledStartAt).toBe('시작 예정 시간은 지금 이후로 정해 주세요.');
    expect(validateStreamForm(values({ scheduledStartAt: '2026-10-21T20:00' }), NOW).scheduledStartAt).toBeUndefined();
    expect(validateStreamForm(values({ scheduledStartAt: '2026-11-21T12:00' }), NOW).scheduledStartAt).toContain('30일 이내');
  });
});

describe('toCreateRequest', () => {
  it('앞뒤 공백을 빼고, 비운 설명·예정 시간은 보내지 않는다', () => {
    expect(toCreateRequest(values({ title: ' 방송 ', description: '  ' }))).toEqual({ title: '방송', description: undefined, scheduledStartAt: undefined });
  });

  it('예정 시간은 초를 붙인 서버 시각으로 보낸다', () => {
    expect(toCreateRequest(values({ scheduledStartAt: '2026-10-21T20:00' })).scheduledStartAt).toBe('2026-10-21T20:00:00');
  });
});

describe('toUpdateRequest', () => {
  it('바뀐 것이 없으면 null', () => {
    expect(toUpdateRequest(scheduled, formValuesOf(scheduled))).toBeNull();
  });

  it('바뀐 값만 담는다', () => {
    expect(toUpdateRequest(scheduled, { ...formValuesOf(scheduled), title: '새 제목' })).toEqual({ title: '새 제목' });
  });

  it('설명을 지우면 빈 문자열을 보낸다', () => {
    expect(toUpdateRequest(scheduled, { ...formValuesOf(scheduled), description: '' })).toEqual({ description: '' });
  });

  it('예정 시간은 방송 시작 전에만, 값이 있을 때만 보낸다', () => {
    expect(toUpdateRequest(scheduled, { ...formValuesOf(scheduled), scheduledStartAt: '2026-10-22T09:30' })).toEqual({ scheduledStartAt: '2026-10-22T09:30:00' });
    expect(toUpdateRequest(scheduled, { ...formValuesOf(scheduled), scheduledStartAt: '' })).toBeNull();
    const live = { ...scheduled, status: 'LIVE' as const };
    expect(toUpdateRequest(live, { ...formValuesOf(live), scheduledStartAt: '2026-10-22T09:30' })).toBeNull();
  });
});

describe('dateTimeInputOf', () => {
  it('datetime-local 입력값 형식으로 바꾼다', () => {
    expect(dateTimeInputOf(new Date(2026, 0, 5, 7, 3))).toBe('2026-01-05T07:03');
  });
});

describe('prependModerationAlert', () => {
  const alert = (messageId: string): ModerationAlert => ({
    liveStreamId: 1,
    messageId,
    senderId: 2,
    nickname: '시청자',
    content: '010-1234-5678로 연락 주세요',
    category: 'EXTERNAL_TRADE',
    categoryLabel: '외부 거래 유도',
    reason: '연락처를 남겨 외부 거래를 유도함',
    detectedAt: '2026-10-21T12:00:00',
  });

  it('최신 알림을 맨 앞에 붙이고, 같은 채팅은 한 번만 남긴다', () => {
    const list = prependModerationAlert([alert('a')], alert('b'));
    expect(list.map((a) => a.messageId)).toEqual(['b', 'a']);
    expect(prependModerationAlert(list, alert('a'))).toBe(list);
  });

  it('최대 개수를 넘으면 오래된 것부터 지운다', () => {
    expect(prependModerationAlert([alert('a'), alert('b')], alert('c'), 2).map((a) => a.messageId)).toEqual(['c', 'a']);
  });
});

describe('mediaErrorMessage', () => {
  const error = (name: string) => Object.assign(new Error(name), { name });

  it('권한 거부·장치 없음·사용 중을 구분한다', () => {
    expect(mediaErrorMessage('camera', error('NotAllowedError'))).toContain('카메라 사용이 막혀 있어요');
    expect(mediaErrorMessage('microphone', error('NotFoundError'))).toContain('연결된 마이크를 찾지 못했어요');
    expect(mediaErrorMessage('camera', error('NotReadableError'))).toContain('다른 프로그램이 카메라를 쓰고 있어요');
  });

  it('모르는 오류는 다시 시도 안내', () => {
    expect(mediaErrorMessage('microphone', 'oops')).toBe('마이크를 켜지 못했어요. 다시 시도해 주세요.');
  });
});
