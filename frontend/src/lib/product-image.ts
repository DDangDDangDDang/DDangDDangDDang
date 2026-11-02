/*
 * 상품 이미지 고르기. 서버(ProductImageResolver)와 같은 규칙으로 올리기 전에 먼저 걸러 낸다.
 */

/** 서버가 받는 형식 (JPG, PNG, WEBP, GIF) */
export const PRODUCT_IMAGE_TYPES = ['image/jpeg', 'image/png', 'image/webp', 'image/gif'] as const;
export const PRODUCT_IMAGE_MAX_BYTES = 10 * 1024 * 1024;
/** 상품 이미지(갤러리)는 10장까지. 첫 장이 대표 이미지다. */
export const PRODUCT_IMAGE_MAX_COUNT = 10;

/** 화면에 들고 있는 이미지 한 장: 저장소 키(서버에 보낸다)와 보여 줄 주소 */
export type ProductImage = { key: string; url: string };

/** 올릴 수 없는 파일이면 이유, 올릴 수 있으면 undefined */
export function productImageProblem(file: { type: string; size: number }): string | undefined {
  if (!(PRODUCT_IMAGE_TYPES as readonly string[]).includes(file.type)) return 'JPG, PNG, WEBP, GIF 이미지만 올릴 수 있어요.';
  if (file.size === 0) return '빈 파일은 올릴 수 없어요.';
  if (file.size > PRODUCT_IMAGE_MAX_BYTES) return '10MB 이하 이미지만 올릴 수 있어요.';
  return undefined;
}

/** index의 이미지를 offset만큼(-1: 앞으로, 1: 뒤로) 옮긴다. 끝을 넘으면 그대로 둔다. */
export function moveImage<T>(images: T[], index: number, offset: -1 | 1): T[] {
  const target = index + offset;
  if (index < 0 || index >= images.length || target < 0 || target >= images.length) return images;
  const next = [...images];
  next.splice(target, 0, ...next.splice(index, 1));
  return next;
}
