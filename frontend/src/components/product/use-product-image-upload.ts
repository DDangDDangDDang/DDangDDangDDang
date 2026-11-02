import { useIsMutating, useMutation } from '@tanstack/react-query';
import { ApiError, api, call, uploadToSignedUrl } from '@/api';
import type { ProductImage } from '@/lib/product-image';

/** 상품 이미지 업로드 (갤러리·설명 공용). 화면은 이 키로 올리는 중인지 본다. */
const PRODUCT_IMAGE_UPLOAD_KEY = ['product-image-upload'] as const;

/**
 * 상품 이미지 한 장 올리기: 업로드 URL 받기 → 저장소(S3)에 바로 올리기. 키는 상품을 저장할 때 서버가 확인한다.
 * 갤러리와 설명 속 이미지가 같이 쓴다.
 */
export function useProductImageUpload(onUploaded: (image: ProductImage) => void) {
  return useMutation({
    mutationKey: PRODUCT_IMAGE_UPLOAD_KEY,
    mutationFn: async (file: File): Promise<ProductImage> => {
      const signed = await call(api.POST('/api/v1/products/image-uploads', { body: { contentType: file.type, contentLength: file.size } }));
      if (!signed?.uploadUrl || !signed.imageKey || !signed.imageUrl) throw new ApiError('UPLOAD_FAILED', '업로드 URL을 받지 못했어요.', 0);
      await uploadToSignedUrl(signed.uploadUrl, file);
      return { key: signed.imageKey, url: signed.imageUrl };
    },
    onSuccess: onUploaded,
  });
}

/**
 * 상품 이미지를 올리는 중인지. 올리는 중에 저장하면 아직 안 끝난 이미지가 상품에 붙지 않으므로 등록·저장을 막는다.
 * (갤러리는 여러 장을 하나씩 이어 올리지만, 다음 장은 앞 장이 끝난 바로 그 자리에서 시작해 사이에 눌릴 틈이 없다)
 */
export function useProductImageUploading(): boolean {
  return useIsMutating({ mutationKey: PRODUCT_IMAGE_UPLOAD_KEY }) > 0;
}
