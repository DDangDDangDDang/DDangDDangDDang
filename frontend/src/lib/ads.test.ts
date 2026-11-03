import { describe, expect, it } from 'vitest';
import { adImageProblem, adLinkProblem, recommendedAdImageSize } from './ads';

describe('광고 규칙', () => {
  it('링크는 http·https만 받는다', () => {
    expect(adLinkProblem('https://example.com/sale')).toBeUndefined();
    expect(adLinkProblem('http://example.com')).toBeUndefined();
    expect(adLinkProblem('')).toBe('광고를 눌렀을 때 열 주소를 입력해 주세요.');
    expect(adLinkProblem('javascript:alert(1)')).toBe('http:// 또는 https://로 시작하는 주소만 쓸 수 있어요.');
    expect(adLinkProblem('example.com')).toBe('http:// 또는 https://로 시작하는 주소를 입력해 주세요.');
  });

  it('이미지는 5MB 이하 JPG·PNG·WEBP·GIF', () => {
    expect(adImageProblem({ type: 'image/png', size: 1000 })).toBeUndefined();
    expect(adImageProblem({ type: 'application/pdf', size: 1000 })).toBe('JPG, PNG, WEBP, GIF 이미지만 올릴 수 있어요.');
    expect(adImageProblem({ type: 'image/png', size: 6 * 1024 * 1024 })).toBe('5MB 이하 이미지만 올릴 수 있어요.');
  });

  it('자리별 권장 이미지 크기는 화면 크기의 2배', () => {
    expect(recommendedAdImageSize('HOME_TOP')).toBe('2048 × 256');
    expect(recommendedAdImageSize('SIDEBAR')).toBe('480 × 640');
  });
});
