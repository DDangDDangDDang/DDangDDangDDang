import { useMutation } from '@tanstack/react-query';
import { api, call, uploadToSignedUrl } from '@/api';
import type { ProductImage } from '@/lib/product-image';

/**
 * 상품 이미지 한 장 올리기: 업로드 URL 받기 → 저장소(S3)에 바로 올리기. 키는 상품을 저장할 때 서버가 확인한다.
 * 갤러리와 설명 속 이미지가 같이 쓴다.
 */
export function useProductImageUpload(onUploaded: (image: ProductImage) => void) {
  return useMutation({
    mutationFn: async (file: File): Promise<ProductImage> => {
      const signed = await call(api.POST('/api/v1/products/image-uploads', { body: { contentType: file.type, contentLength: file.size } }));
      if (!signed?.uploadUrl || !signed.imageKey || !signed.imageUrl) throw new Error('업로드 URL을 받지 못했어요.');
      await uploadToSignedUrl(signed.uploadUrl, file);
      return { key: signed.imageKey, url: signed.imageUrl };
    },
    onSuccess: onUploaded,
  });
}
