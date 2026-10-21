import { describe, expect, it } from 'vitest';
import { productFormValuesOf, toProductRequest, validateProductForm } from './product-form';

describe('validateProductForm', () => {
  it('카테고리·상품명이 있으면 통과한다', () => {
    expect(validateProductForm({ categoryId: '11', name: '필름 카메라', description: '' })).toEqual({});
  });

  it('카테고리·상품명이 없거나 상품명이 50자를 넘으면 오류', () => {
    const errors = validateProductForm({ categoryId: '', name: '  ', description: '' });
    expect(errors.categoryId).toBe('카테고리를 골라 주세요.');
    expect(errors.name).toBe('상품명을 입력해 주세요.');
    expect(validateProductForm({ categoryId: '1', name: '가'.repeat(51), description: '' }).name).toBeDefined();
  });
});

describe('toProductRequest', () => {
  it('앞뒤 공백을 빼고, 비운 설명은 보내지 않는다', () => {
    expect(toProductRequest({ categoryId: '11', name: ' 필름 카메라 ', description: '  ' })).toEqual({ categoryId: 11, name: '필름 카메라', description: undefined });
  });

  it('수정 화면은 지금 값으로 채운다', () => {
    expect(productFormValuesOf({ categoryId: 11, name: '렌즈', description: '상태 좋음' })).toEqual({ categoryId: '11', name: '렌즈', description: '상태 좋음' });
    expect(productFormValuesOf(undefined)).toEqual({ categoryId: '', name: '', description: '' });
  });
});
