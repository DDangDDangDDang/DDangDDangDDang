import type { Schema } from '@/api';
import { parseDescription } from '@/lib/product-description';

/**
 * 상품 설명 보기: 글은 글자 그대로(줄바꿈 유지), "![](키)" 줄은 서버가 확인해 준 이미지(descriptionImages)로 그린다.
 * HTML로 넣지 않고 React가 글자로 그리므로 설명에 태그·스크립트가 있어도 실행되지 않는다.
 * 서버가 주소를 주지 않은 키는 적힌 글자 그대로 보여 준다.
 */
export function ProductDescription({ description, images }: { description: string | undefined; images: Schema<'ProductImageResponse'>[] | undefined }) {
  const urls = new Map((images ?? []).flatMap((image) => (image.key && image.url ? [[image.key, image.url] as const] : [])));
  const blocks = parseDescription(description);

  return (
    <div className="flex flex-col gap-3">
      {blocks.map((block, index) => {
        if (block.type === 'image') {
          const url = urls.get(block.key);
          return url ? (
            <img key={index} src={url} alt="상품 설명 이미지" className="max-w-full self-start rounded-md" loading="lazy" />
          ) : (
            <p key={index} className="text-body-lg leading-relaxed whitespace-pre-line text-neutral-700">
              {`![](${block.key})`}
            </p>
          );
        }
        return block.text.trim() === '' ? null : (
          <p key={index} className="text-body-lg leading-relaxed whitespace-pre-line text-neutral-700">
            {block.text.replace(/^\n+|\n+$/g, '')}
          </p>
        );
      })}
    </div>
  );
}
