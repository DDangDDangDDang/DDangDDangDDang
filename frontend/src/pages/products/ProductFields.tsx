import { ImagePlus } from 'lucide-react';
import { Input, Notice, Select, Textarea } from '@/components/ui';
import { leafCategoryOptions } from '@/lib/auction';
import type { Schema } from '@/api';
import { PRODUCT_DESCRIPTION_MAX_LENGTH, PRODUCT_NAME_MAX_LENGTH, type ProductFormErrors, type ProductFormValues } from './product-form';

type Props = {
  categories: Schema<'CategoryResponse'>[];
  values: ProductFormValues;
  errors: ProductFormErrors;
  onChange: (patch: Partial<ProductFormValues>) => void;
};

/** 상품 정보 입력: 카테고리(소분류)·상품명·설명. 이미지는 업로드 API가 없어 안내만 한다. */
export function ProductFields({ categories, values, errors, onChange }: Props) {
  return (
    <div className="flex flex-col gap-4">
      <div className="grid grid-cols-2 gap-4">
        <Select
          label="카테고리"
          options={[{ value: '', label: '카테고리 선택' }, ...leafCategoryOptions(categories)]}
          value={values.categoryId}
          onChange={(e) => onChange({ categoryId: e.target.value })}
          error={errors.categoryId}
        />
        <Input
          label={`상품명 (${PRODUCT_NAME_MAX_LENGTH}자 이하)`}
          placeholder="예) 캐논 AE-1 필름 카메라"
          maxLength={PRODUCT_NAME_MAX_LENGTH}
          value={values.name}
          onChange={(e) => onChange({ name: e.target.value })}
          error={errors.name}
        />
      </div>
      <Textarea
        label="상품 설명"
        rows={5}
        placeholder="상태, 구성품, 사이즈 등을 적어 주세요."
        maxLength={PRODUCT_DESCRIPTION_MAX_LENGTH}
        value={values.description}
        onChange={(e) => onChange({ description: e.target.value })}
        error={errors.description}
        hint={
          <span>
            <span className="num">{values.description.length}</span>/{PRODUCT_DESCRIPTION_MAX_LENGTH}
          </span>
        }
      />
      <div className="flex flex-col gap-1.5">
        <span className="text-label font-semibold text-neutral-900">상품 이미지</span>
        <div className="flex items-center gap-3">
          <span className="flex size-20 items-center justify-center rounded-md border border-dashed border-neutral-400 bg-neutral-25 text-neutral-400">
            <ImagePlus className="size-6" aria-hidden="true" />
          </span>
          <Notice className="flex-1">이미지 올리기는 준비하고 있어요. 지금은 상품명과 설명으로 등록돼요.</Notice>
        </div>
      </div>
    </div>
  );
}
