import type { Schema } from '@/api';
import { DESCRIPTION_IMAGE_MAX_COUNT, descriptionImageKeys, descriptionTextLength } from '@/lib/product-description';
import { PRODUCT_IMAGE_MAX_COUNT, type ProductImage } from '@/lib/product-image';

/*
 * 상품 등록·수정 입력 검사와 요청 본문. (화면과 분리해 단위 테스트한다)
 */

/** 서버 검증과 같은 한도 (ProductRequest: 상품명 50자) */
export const PRODUCT_NAME_MAX_LENGTH = 50;
/** 설명 글자 수(이미지 줄 제외). 서버는 이미지 줄을 포함해 5000자까지 받는다. */
export const PRODUCT_DESCRIPTION_MAX_LENGTH = 2000;

/**
 * description: 글 사이 이미지는 "![](키)" 한 줄로 들어 있다. (@/lib/product-description)
 * images: 상품 이미지(첫 장이 대표). descriptionImageUrls: 설명 속 이미지 키 → 보여 줄 주소
 */
export type ProductFormValues = {
  categoryId: string;
  name: string;
  description: string;
  images: ProductImage[];
  descriptionImageUrls: Record<string, string>;
};
/** 입력 변경: 바뀐 값, 또는 이전 값으로 계산하는 함수 (이미지 업로드가 끝나는 시점에 붙일 때) */
export type ProductFormChange = Partial<ProductFormValues> | ((prev: ProductFormValues) => Partial<ProductFormValues>);

export function applyProductChange(prev: ProductFormValues, change: ProductFormChange): ProductFormValues {
  return { ...prev, ...(typeof change === 'function' ? change(prev) : change) };
}

export type ProductFormErrors =Partial<Record<'categoryId' | 'name' | 'description' | 'images', string>>;

export function productFormValuesOf(product?: Schema<'ProductResponse'>): ProductFormValues {
  return {
    categoryId: product?.categoryId ? String(product.categoryId) : '',
    name: product?.name ?? '',
    description: product?.description ?? '',
    images: toImages(product?.images),
    descriptionImageUrls: Object.fromEntries(toImages(product?.descriptionImages).map((image) => [image.key, image.url])),
  };
}

function toImages(images: Schema<'ProductImageResponse'>[] | undefined): ProductImage[] {
  return (images ?? []).flatMap((image) => (image.key && image.url ? [{ key: image.key, url: image.url }] : []));
}

export function validateProductForm(values: ProductFormValues): ProductFormErrors {
  const errors: ProductFormErrors = {};
  if (!values.categoryId) errors.categoryId = '카테고리를 골라 주세요.';
  const name = values.name.trim();
  if (!name) errors.name = '상품명을 입력해 주세요.';
  else if (name.length > PRODUCT_NAME_MAX_LENGTH) errors.name = `상품명은 ${PRODUCT_NAME_MAX_LENGTH}자 이하로 입력해 주세요.`;
  if (descriptionTextLength(values.description) > PRODUCT_DESCRIPTION_MAX_LENGTH) errors.description = `상품 설명은 ${PRODUCT_DESCRIPTION_MAX_LENGTH}자 이하로 입력해 주세요.`;
  else if (descriptionImageKeys(values.description).length > DESCRIPTION_IMAGE_MAX_COUNT) errors.description = `설명에는 이미지를 ${DESCRIPTION_IMAGE_MAX_COUNT}장까지 넣을 수 있어요.`;
  if (values.images.length > PRODUCT_IMAGE_MAX_COUNT) errors.images = `상품 이미지는 ${PRODUCT_IMAGE_MAX_COUNT}장까지 올릴 수 있어요.`;
  return errors;
}

/**
 * 등록·수정 공용. 설명(비웠어도)과 imageKeys는 항상 보내, 수정에서도 지운 글·이미지와 순서가 그대로 반영된다.
 * (서버는 보내지 않은 값을 그대로 두므로, 비운 설명을 빼고 보내면 예전 설명과 그 속 이미지가 남는다)
 */
export function toProductRequest(values: ProductFormValues): Schema<'ProductRequest'> {
  return {
    categoryId: Number(values.categoryId),
    name: values.name.trim(),
    description: values.description.trim(),
    imageKeys: values.images.map((image) => image.key),
  };
}
