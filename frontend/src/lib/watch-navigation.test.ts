import { describe, expect, it } from 'vitest';
import { isWatchPath, shouldOpenInNewTab } from './watch-navigation';

const origin = 'http://localhost:5173';
const url = (path: string) => new URL(path, origin);

describe('watch-navigation', () => {
  it('방송 시청 화면만 시청 화면으로 본다', () => {
    expect(isWatchPath('/lives/12')).toBe(true);
    expect(isWatchPath('/lives')).toBe(false);
    expect(isWatchPath('/lives/12/edit')).toBe(false);
  });

  it('시청 중에 다른 화면으로 가면 새 탭, 같은 방송·로그인·외부 주소·시청 중이 아니면 이 탭', () => {
    expect(shouldOpenInNewTab('/lives/12', url('/auctions/3'), origin)).toBe(true);
    expect(shouldOpenInNewTab('/lives/12', url('/lives/13'), origin)).toBe(true);
    expect(shouldOpenInNewTab('/lives/12', url('/lives/12'), origin)).toBe(false);
    expect(shouldOpenInNewTab('/lives/12', url('/login?redirect=%2Flives%2F12'), origin)).toBe(false);
    expect(shouldOpenInNewTab('/lives/12', new URL('https://example.com/x'), origin)).toBe(false);
    expect(shouldOpenInNewTab('/auctions', url('/auctions/3'), origin)).toBe(false);
  });
});
