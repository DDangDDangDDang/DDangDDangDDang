import { describe, expect, it } from 'vitest';
import { moveImage, productImageProblem } from './product-image';

describe('productImageProblem', () => {
  it('이미지 형식·크기를 서버와 같은 규칙으로 거른다', () => {
    expect(productImageProblem({ type: 'image/png', size: 1024 })).toBeUndefined();
    expect(productImageProblem({ type: 'image/svg+xml', size: 1024 })).toMatch('JPG');
    expect(productImageProblem({ type: 'image/jpeg', size: 0 })).toMatch('빈 파일');
    expect(productImageProblem({ type: 'image/jpeg', size: 10 * 1024 * 1024 + 1 })).toMatch('10MB');
  });
});

describe('moveImage', () => {
  it('앞뒤로 한 칸 옮기고, 끝을 넘으면 그대로 둔다', () => {
    expect(moveImage(['a', 'b', 'c'], 1, -1)).toEqual(['b', 'a', 'c']);
    expect(moveImage(['a', 'b', 'c'], 1, 1)).toEqual(['a', 'c', 'b']);
    const same = ['a', 'b'];
    expect(moveImage(same, 0, -1)).toBe(same);
    expect(moveImage(same, 1, 1)).toBe(same);
  });
});
