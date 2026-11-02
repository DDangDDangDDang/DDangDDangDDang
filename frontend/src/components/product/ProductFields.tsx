import { Input, Select } from '@/components/ui';
import { leafCategoryOptions } from '@/lib/auction';
import type { Schema } from '@/api';
import { PRODUCT_NAME_MAX_LENGTH, type ProductFormChange, type ProductFormErrors, type ProductFormValues } from '@/lib/product-form';
import { ProductDescriptionEditor } from './ProductDescriptionEditor';
import { ProductImagesField } from './ProductImagesField';

type Props = {
  categories: Schema<'CategoryResponse'>[];
  values: ProductFormValues;
  errors: ProductFormErrors;
  onChange: (change: ProductFormChange) => void;
};

/** 상품 정보 입력: 카테고리(소분류)·상품명·상품 이미지(갤러리)·설명(글 사이 이미지). */
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
      <ProductImagesField images={values.images} error={errors.images} onChange={(change) => onChange((prev) => ({ images: change(prev.images) }))} />
      <ProductDescriptionEditor description={values.description} imageUrls={values.descriptionImageUrls} error={errors.description} onChange={onChange} />
    </div>
  );
}
