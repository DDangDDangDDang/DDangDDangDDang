import { describe, expect, it } from 'vitest';
import type { Schema } from '@/api';
import {
  formValuesOf,
  toCreateRequest,
  toUpdateRequest,
  validateStreamForm,
  type StreamFormValues,
} from './studio';

const NOW = new Date('2026-10-21T12:00:00');
const values = (patch: Partial<StreamFormValues> = {}): StreamFormValues => ({ title: '필름 카메라 경매', description: '', scheduledStartAt: '', hashtags: [], ...patch });

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
    expect(toCreateRequest(values({ title: ' 방송 ', description: '  ' }))).toEqual({ title: '방송', description: undefined, scheduledStartAt: undefined, hashtags: undefined });
  });

  it('해시태그가 있으면 보낸다', () => {
    expect(toCreateRequest(values({ hashtags: ['나이키', '운동화'] })).hashtags).toEqual(['나이키', '운동화']);
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

  it('해시태그가 바뀌면 목록 전체를, 모두 지우면 빈 목록을 보낸다', () => {
    const tagged = { ...scheduled, hashtags: ['나이키'] };
    expect(toUpdateRequest(tagged, formValuesOf(tagged))).toBeNull();
    expect(toUpdateRequest(tagged, { ...formValuesOf(tagged), hashtags: ['나이키', '운동화'] })).toEqual({ hashtags: ['나이키', '운동화'] });
    expect(toUpdateRequest(tagged, { ...formValuesOf(tagged), hashtags: [] })).toEqual({ hashtags: [] });
  });

  it('예정 시간은 방송 시작 전에만, 값이 있을 때만 보낸다', () => {
    expect(toUpdateRequest(scheduled, { ...formValuesOf(scheduled), scheduledStartAt: '2026-10-22T09:30' })).toEqual({ scheduledStartAt: '2026-10-22T09:30:00' });
    expect(toUpdateRequest(scheduled, { ...formValuesOf(scheduled), scheduledStartAt: '' })).toBeNull();
    const live = { ...scheduled, status: 'LIVE' as const };
    expect(toUpdateRequest(live, { ...formValuesOf(live), scheduledStartAt: '2026-10-22T09:30' })).toBeNull();
  });
});

