import { useState } from 'react';
import { Select } from '@/components/ui';
import { childCategoryOptions, parentCategoryOf, parentCategoryOptions } from '@/lib/auction';
import type { Schema } from '@/api';

type Props = {
  categories: Schema<'CategoryResponse'>[];
  /** 고른 카테고리 id. 비었으면 '' */
  value: string;
  onChange: (id: string) => void;
  /**
   * leaf: 상품 등록. 소분류까지 골라야 값이 생긴다. (서버는 소분류만 받는다, PRODUCT_003)
   * filter: 목록 거르기. 대분류만 골라도 그 대분류 전체로 거른다.
   */
  mode: 'leaf' | 'filter';
  /** [1차, 2차] 라벨 */
  labels: [string, string];
  hideLabel?: boolean;
  disabled?: boolean;
  error?: string;
};

/** 카테고리 선택: 1차(대분류)를 먼저 고르고, 그 아래 2차(소분류)를 고른다. */
export function CategorySelects({ categories, value, onChange, mode, labels, hideLabel, disabled, error }: Props) {
  // 상품 등록에서 대분류만 고른 동안은 값('')으로 대분류를 알 수 없어 따로 기억한다.
  const [pickedParentId, setPickedParentId] = useState('');
  const parent = parentCategoryOf(categories, value) ?? parentCategoryOf(categories, pickedParentId);
  const parentId = parent?.id === undefined ? '' : String(parent.id);
  const childId = value && value !== parentId ? value : '';
  const leaf = mode === 'leaf';

  return (
    <div className="grid grid-cols-2 gap-2">
      <Select
        label={labels[0]}
        hideLabel={hideLabel}
        options={[{ value: '', label: leaf ? '대분류 선택' : '전체 카테고리' }, ...parentCategoryOptions(categories, leaf)]}
        value={parentId}
        disabled={disabled}
        // 오류는 아직 고르지 않은 쪽 아래에 보인다.
        error={parentId ? undefined : error}
        onChange={(e) => {
          setPickedParentId(e.target.value);
          onChange(leaf ? '' : e.target.value);
        }}
      />
      <Select
        label={labels[1]}
        hideLabel={hideLabel}
        options={[{ value: '', label: leaf ? '소분류 선택' : '전체' }, ...childCategoryOptions(parent)]}
        value={childId}
        disabled={disabled || !parent || childCategoryOptions(parent).length === 0}
        error={parentId ? error : undefined}
        onChange={(e) => onChange(e.target.value || (leaf ? '' : parentId))}
      />
    </div>
  );
}
