import { describe, expect, it } from 'vitest';
import { applyProductChange, productFormValuesOf, toProductRequest, validateProductForm, type ProductFormValues } from './product-form';

const A = 'product-images/7/0f8fad5b-d9cb-469f-a165-70867728950e.png';
const empty: ProductFormValues = { categoryId: '', name: '', description: '', images: [], descriptionImageUrls: {} };

describe('validateProductForm', () => {
  it('카테고리·상품명이 있으면 통과한다', () => {
    expect(validateProductForm({ ...empty, categoryId: '11', name: '필름 카메라' })).toEqual({});
  });

  it('카테고리·상품명이 없거나 상품명이 50자를 넘으면 오류', () => {
    const errors = validateProductForm({ ...empty, name: '  ' });
    expect(errors.categoryId).toBe('카테고리를 골라 주세요.');
    expect(errors.name).toBe('상품명을 입력해 주세요.');
    expect(validateProductForm({ ...empty, categoryId: '1', name: '가'.repeat(51) }).name).toBeDefined();
  });

  it('설명 글자 수는 이미지 줄을 빼고 세고, 이미지는 갤러리 10장·설명 20장까지', () => {
    const ok = { ...empty, categoryId: '1', name: '렌즈' };
    expect(validateProductForm({ ...ok, description: `${'가'.repeat(2000)}\n![](${A})` })).toEqual({});
    expect(validateProductForm({ ...ok, description: '가'.repeat(2001) }).description).toBeDefined();
    const images = Array.from({ length: 11 }, (_, i) => ({ key: `k${i}`, url: `u${i}` }));
    expect(validateProductForm({ ...ok, images }).images).toBeDefined();
    const many = Array.from({ length: 21 }, (_, i) => `![](product-images/7/${i}.png)`).join('\n');
    expect(validateProductForm({ ...ok, description: many }).description).toBeDefined();
  });
});

describe('toProductRequest', () => {
  it('앞뒤 공백을 빼고, 비운 설명도 보내 수정에서 지워지게 한다. 이미지 키는 순서대로 보낸다', () => {
    expect(toProductRequest({ ...empty, categoryId: '11', name: ' 필름 카메라 ', description: '  ', images: [{ key: 'b', url: 'ub' }, { key: 'a', url: 'ua' }] })).toEqual({
      categoryId: 11,
      name: '필름 카메라',
      description: '',
      imageKeys: ['b', 'a'],
    });
  });

  it('수정 화면은 지금 값으로 채운다', () => {
    expect(
      productFormValuesOf({
        categoryId: 11,
        name: '렌즈',
        description: `상태 좋음\n![](${A})`,
        images: [{ key: 'b', url: 'ub' }],
        descriptionImages: [{ key: A, url: 'ua' }],
      }),
    ).toEqual({ categoryId: '11', name: '렌즈', description: `상태 좋음\n![](${A})`, images: [{ key: 'b', url: 'ub' }], descriptionImageUrls: { [A]: 'ua' } });
    expect(productFormValuesOf(undefined)).toEqual(empty);
  });
});

describe('applyProductChange', () => {
  it('바뀐 값이나 이전 값으로 계산하는 함수를 합친다', () => {
    expect(applyProductChange(empty, { name: '렌즈' }).name).toBe('렌즈');
    expect(applyProductChange({ ...empty, images: [{ key: 'a', url: 'u' }] }, (prev) => ({ images: [...prev.images, { key: 'b', url: 'v' }] })).images).toHaveLength(2);
  });
});
