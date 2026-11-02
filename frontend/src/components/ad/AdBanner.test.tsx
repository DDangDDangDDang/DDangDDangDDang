import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { act, fireEvent, render, screen } from '@testing-library/react';
import type { ReactNode } from 'react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import { AD_SLIDE_MS, type Ad } from '@/lib/ads';
import { AdBanner } from './AdBanner';

// 서버에 걸린 광고: 없음 (ads를 넘기지 않은 경우에만 부른다)
vi.mock('@/api', async (importOriginal) => {
  const original = await importOriginal<typeof import('@/api')>();
  return { ...original, api: { GET: () => ({}) }, call: async () => [] };
});

const AD1: Ad = { imageUrl: '/ad1.png', linkUrl: 'https://example.com/1', alt: '가을 세일' };
const AD2: Ad = { imageUrl: '/ad2.png', linkUrl: 'https://example.com/2', alt: '겨울 이벤트' };
const AD3: Ad = { imageUrl: '/ad3.png', linkUrl: 'https://example.com/3', alt: '신규 가입 혜택' };

function wrap(children: ReactNode) {
  return <QueryClientProvider client={new QueryClient({ defaultOptions: { queries: { retry: false } } })}>{children}</QueryClientProvider>;
}

/** 지금 보이는 광고 번호 (점 버튼의 aria-current) */
function currentDot() {
  return screen.getAllByRole('button', { name: /번째 광고 보기/ }).findIndex((dot) => dot.getAttribute('aria-current') === 'true') + 1;
}

describe('AdBanner', () => {
  afterEach(() => {
    vi.useRealTimers();
  });

  it('걸린 광고가 없으면 광고 자리와 권장 이미지 크기를 보여준다', async () => {
    render(wrap(<AdBanner placement="home-top" />));
    const slot = await screen.findByRole('note', { name: '광고 자리' });
    expect(slot).toHaveTextContent('2048 × 256');
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('광고가 1개면 고정으로 보여주고 "광고" 표시와 함께 새 탭으로 연다', () => {
    render(wrap(<AdBanner placement="sidebar" ads={[AD1]} />));
    const link = screen.getByRole('link', { name: /가을 세일/ });
    expect(link).toHaveAttribute('href', 'https://example.com/1');
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer sponsored');
    expect(link).toHaveTextContent('광고');
    expect(screen.queryByRole('button', { name: '다음 광고' })).not.toBeInTheDocument();
  });

  it('광고가 2개 이상이면 6초마다 다음 광고로 넘어가고, 마지막 다음엔 처음으로 돌아온다', () => {
    vi.useFakeTimers();
    render(wrap(<AdBanner placement="home-top" ads={[AD1, AD2]} />));
    expect(currentDot()).toBe(1);

    act(() => vi.advanceTimersByTime(AD_SLIDE_MS));
    expect(currentDot()).toBe(2);

    act(() => vi.advanceTimersByTime(AD_SLIDE_MS));
    expect(currentDot()).toBe(1);
  });

  it('좌우 화살표로 넘기고, 첫 광고에서 이전을 누르면 마지막 광고로 간다', () => {
    vi.useFakeTimers();
    render(wrap(<AdBanner placement="home-top" ads={[AD1, AD2, AD3]} />));

    fireEvent.click(screen.getByRole('button', { name: '다음 광고' }));
    expect(currentDot()).toBe(2);

    fireEvent.click(screen.getByRole('button', { name: '이전 광고' }));
    expect(currentDot()).toBe(1);

    fireEvent.click(screen.getByRole('button', { name: '이전 광고' }));
    act(() => vi.advanceTimersByTime(50)); // 끝으로 옮긴 뒤 다음 그림에서 마지막 광고로 움직인다
    expect(currentDot()).toBe(3);
  });

  it('일시정지하면 자동으로 넘어가지 않고, 다시 재생하면 넘어간다', () => {
    vi.useFakeTimers();
    render(wrap(<AdBanner placement="home-top" ads={[AD1, AD2]} />));

    fireEvent.click(screen.getByRole('button', { name: '자동 넘김 멈추기' }));
    act(() => vi.advanceTimersByTime(AD_SLIDE_MS * 2));
    expect(currentDot()).toBe(1);

    fireEvent.click(screen.getByRole('button', { name: '자동 넘김 다시 시작' }));
    act(() => vi.advanceTimersByTime(AD_SLIDE_MS));
    expect(currentDot()).toBe(2);
  });

  it('지금 보이지 않는 광고는 키보드로 가지 않는다', () => {
    render(wrap(<AdBanner placement="home-top" ads={[AD1, AD2]} />));
    const links = screen.getAllByRole('link', { hidden: true });
    expect(links.filter((link) => link.getAttribute('tabindex') !== '-1')).toHaveLength(1);
  });
});
