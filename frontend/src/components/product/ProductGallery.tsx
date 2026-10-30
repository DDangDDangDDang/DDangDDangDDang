import { useState } from 'react';
import type { Schema } from '@/api';
import { ProductImage } from '@/components/order/OrderBits';
import { cn } from '@/lib/cn';

/**
 * 상품 이미지 갤러리 (경매 상세): 큰 이미지 + 아래 작은 이미지 목록. 작은 이미지를 누르면 큰 이미지가 바뀐다.
 * 이미지가 없으면 빈 자리(상자 아이콘)를 그린다.
 */
export function ProductGallery({ name, images }: { name: string; images: Schema<'ProductImageResponse'>[] | undefined }) {
  const list = (images ?? []).filter((image) => image.url);
  const [selected, setSelected] = useState(0);
  const current = list[Math.min(selected, list.length - 1)];

  return (
    <div className="flex flex-col gap-3">
      <ProductImage src={current?.url} alt={name} className="aspect-[4/3] w-full rounded-lg object-contain" />
      {list.length > 1 && (
        <ul className="flex flex-wrap gap-2" aria-label="상품 이미지 고르기">
          {list.map((image, index) => (
            <li key={image.key ?? index}>
              <button
                type="button"
                aria-label={`${name} 이미지 ${index + 1} 보기`}
                aria-pressed={image === current}
                onClick={() => setSelected(index)}
                className={cn('block size-16 overflow-hidden rounded-md border-2', image === current ? 'border-neutral-900' : 'border-transparent hover:border-neutral-200')}
              >
                <img src={image.url} alt="" className="size-full object-cover" />
              </button>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
