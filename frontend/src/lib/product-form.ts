import type { Schema } from '@/api';

/*
 * 상품 등록·수정 입력 검사와 요청 본문. (화면과 분리해 단위 테스트한다)
 */

/** 서버 검증과 같은 한도 (ProductRequest: 상품명 50자) */
export const PRODUCT_NAME_MAX_LENGTH = 50;
/** 설명은 서버 한도가 없지만 화면이 감당할 만큼으로 막는다. */
export const PRODUCT_DESCRIPTION_MAX_LENGTH = 2000;

export type ProductFormValues = { categoryId: string; name: string; description: string };
export type ProductFormErrors = Partial<Record<keyof ProductFormValues, string>>;

export function productFormValuesOf(product?: Schema<'ProductResponse'>): ProductFormValues {
  return {
    categoryId: product?.categoryId ? String(product.categoryId) : '',
    name: product?.name ?? '',
    description: product?.description ?? '',
  };
}

export function validateProductForm(values: ProductFormValues): ProductFormErrors {
  const errors: ProductFormErrors = {};
  if (!values.categoryId) errors.categoryId = '카테고리를 골라 주세요.';
  const name = values.name.trim();
  if (!name) errors.name = '상품명을 입력해 주세요.';
  else if (name.length > PRODUCT_NAME_MAX_LENGTH) errors.name = `상품명은 ${PRODUCT_NAME_MAX_LENGTH}자 이하로 입력해 주세요.`;
  if (values.description.trim().length > PRODUCT_DESCRIPTION_MAX_LENGTH) errors.description = `상품 설명은 ${PRODUCT_DESCRIPTION_MAX_LENGTH}자 이하로 입력해 주세요.`;
  return errors;
}

export function toProductRequest(values: ProductFormValues): Schema<'ProductRequest'> {
  return { categoryId: Number(values.categoryId), name: values.name.trim(), description: values.description.trim() || undefined };
}
