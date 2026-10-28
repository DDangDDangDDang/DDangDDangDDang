import { act, renderHook } from '@testing-library/react';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import { HOVER_INTENT_MS, useHoverIntent } from './use-hover-intent';

describe('useHoverIntent', () => {
  beforeEach(() => vi.useFakeTimers());
  afterEach(() => vi.useRealTimers());

  it('마우스를 정해진 시간만큼 올려 두어야 hovered가 된다', () => {
    const { result } = renderHook(() => useHoverIntent());
    act(() => result.current.onMouseEnter());
    act(() => vi.advanceTimersByTime(HOVER_INTENT_MS - 1));
    expect(result.current.hovered).toBe(false);
    act(() => vi.advanceTimersByTime(1));
    expect(result.current.hovered).toBe(true);
  });

  it('스쳐 지나가면(시간 전에 떠나면) hovered가 되지 않는다', () => {
    const { result } = renderHook(() => useHoverIntent());
    act(() => result.current.onMouseEnter());
    act(() => vi.advanceTimersByTime(HOVER_INTENT_MS / 2));
    act(() => result.current.onMouseLeave());
    act(() => vi.advanceTimersByTime(HOVER_INTENT_MS));
    expect(result.current.hovered).toBe(false);
  });

  it('떠나면 바로 hovered가 풀린다', () => {
    const { result } = renderHook(() => useHoverIntent(100));
    act(() => result.current.onMouseEnter());
    act(() => vi.advanceTimersByTime(100));
    expect(result.current.hovered).toBe(true);
    act(() => result.current.onMouseLeave());
    expect(result.current.hovered).toBe(false);
  });
});
