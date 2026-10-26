import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import type { ChatMessage } from '@/api';
import { useDonationEffects } from './use-donation-effects';

const base = { liveStreamId: 1, senderId: 3, sentAt: '2026-10-24T12:00:00' };
const chat = (id: string): ChatMessage => ({ ...base, messageId: id, type: 'CHAT', nickname: '시청자', content: '안녕하세요' });
const donation = (id: string, nickname: string, amount: number, content: string | null = null): ChatMessage => ({
  ...base,
  messageId: id,
  type: 'DONATION',
  nickname,
  amount,
  content,
});

describe('useDonationEffects', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('새로 온 선물만 하나씩 차례로 보여 주고, 처음부터 있던 선물과 일반 채팅은 보여 주지 않는다', () => {
    const { result, rerender } = renderHook(({ messages }) => useDonationEffects(messages, 1_000), {
      initialProps: { messages: [donation('old', '예전 선물', 300)] },
    });
    expect(result.current).toBeUndefined();

    rerender({ messages: [donation('old', '예전 선물', 300), chat('c1'), donation('d1', '필름팬', 1_000, '최고예요'), donation('d2', '단골', 500)] });
    expect(result.current).toMatchObject({ nickname: '필름팬', amount: 1_000, message: '최고예요' });

    act(() => vi.advanceTimersByTime(1_000));
    expect(result.current).toMatchObject({ nickname: '단골', amount: 500 });

    act(() => vi.advanceTimersByTime(1_000));
    expect(result.current).toBeUndefined();
  });
});
