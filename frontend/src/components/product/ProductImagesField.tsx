import { ChevronLeft, ChevronRight, ImagePlus, X } from 'lucide-react';
import { useRef, useState, type ChangeEvent, type ReactNode } from 'react';
import { errorMessage } from '@/api';
import { Badge, Notice } from '@/components/ui';
import { cn } from '@/lib/cn';
import { moveImage, PRODUCT_IMAGE_MAX_COUNT, PRODUCT_IMAGE_TYPES, productImageProblem, type ProductImage } from '@/lib/product-image';
import { useProductImageUpload } from './use-product-image-upload';

type Props = {
  images: ProductImage[];
  /** 올리는 중에도 여러 장이 이어서 붙으므로 이전 값으로 계산한다. */
  onChange: (change: (prev: ProductImage[]) => ProductImage[]) => void;
  error?: string;
};

/**
 * 상품 이미지(갤러리) 편집: 여러 장 올리기(10장까지), 순서 바꾸기, 지우기. 첫 장이 대표 이미지다.
 * 지운 이미지는 상품을 저장할 때 서버가 저장소에서 지운다.
 */
export function ProductImagesField({ images, onChange, error }: Props) {
  const inputRef = useRef<HTMLInputElement>(null);
  const [problem, setProblem] = useState<string>();
  const upload = useProductImageUpload((image) => onChange((prev) => (prev.length >= PRODUCT_IMAGE_MAX_COUNT ? prev : [...prev, image])));
  const [pending, setPending] = useState(0);

  async function handleFiles(event: ChangeEvent<HTMLInputElement>) {
    const files = [...(event.target.files ?? [])];
    // 같은 파일을 다시 골라도 change가 일어나게 비운다.
    event.target.value = '';
    upload.reset();
    const room = PRODUCT_IMAGE_MAX_COUNT - images.length;
    const accepted = files.filter((file) => !productImageProblem(file)).slice(0, Math.max(room, 0));
    const rejected = files.find((file) => productImageProblem(file));
    setProblem(
      rejected
        ? `${rejected.name}: ${productImageProblem(rejected)}`
        : files.length > accepted.length
          ? `상품 이미지는 ${PRODUCT_IMAGE_MAX_COUNT}장까지 올릴 수 있어요.`
          : undefined,
    );
    setPending(accepted.length);
    // 순서대로 하나씩 올린다. 하나가 실패하면 나머지는 올리지 않는다.
    for (const file of accepted) {
      try {
        await upload.mutateAsync(file);
      } catch {
        break;
      } finally {
        setPending((count) => Math.max(count - 1, 0));
      }
    }
    setPending(0);
  }

  const full = images.length + pending >= PRODUCT_IMAGE_MAX_COUNT;
  const message = error ?? problem ?? (upload.isError ? errorMessage(upload.error) : undefined);

  return (
    <div className="flex flex-col gap-1.5">
      <span className="text-label font-semibold text-neutral-900">
        상품 이미지 <span className="font-normal text-neutral-500">(<span className="num">{images.length}</span>/{PRODUCT_IMAGE_MAX_COUNT}, 첫 장이 대표 이미지)</span>
      </span>
      <ul className="flex flex-wrap gap-3" aria-label="상품 이미지 목록">
        {images.map((image, index) => (
          <li key={image.key} className="group relative size-28 overflow-hidden rounded-md border border-neutral-200 bg-neutral-50">
            <img src={image.url} alt={`상품 이미지 ${index + 1}`} className="size-full object-cover" />
            {index === 0 && (
              <Badge variant="promo" className="absolute top-1.5 left-1.5">
                대표
              </Badge>
            )}
            <button
              type="button"
              aria-label={`상품 이미지 ${index + 1} 지우기`}
              onClick={() => onChange((prev) => prev.filter((item) => item.key !== image.key))}
              className="absolute top-1.5 right-1.5 flex size-7 items-center justify-center rounded-full bg-neutral-900/70 text-neutral-0 hover:bg-neutral-900"
            >
              <X className="size-4" aria-hidden="true" />
            </button>
            <div className="absolute inset-x-1.5 bottom-1.5 flex justify-between">
              <MoveButton label={`상품 이미지 ${index + 1} 앞으로`} disabled={index === 0} onClick={() => onChange((prev) => moveImage(prev, prev.findIndex((item) => item.key === image.key), -1))}>
                <ChevronLeft className="size-4" aria-hidden="true" />
              </MoveButton>
              <MoveButton
                label={`상품 이미지 ${index + 1} 뒤로`}
                disabled={index === images.length - 1}
                onClick={() => onChange((prev) => moveImage(prev, prev.findIndex((item) => item.key === image.key), 1))}
              >
                <ChevronRight className="size-4" aria-hidden="true" />
              </MoveButton>
            </div>
          </li>
        ))}
        {Array.from({ length: pending }, (_, index) => (
          <li key={`pending-${index}`} className="flex size-28 animate-pulse items-center justify-center rounded-md bg-neutral-100 text-caption text-neutral-500">
            올리는 중
          </li>
        ))}
        {!full && (
          <li>
            <button
              type="button"
              onClick={() => inputRef.current?.click()}
              disabled={pending > 0}
              className="flex size-28 flex-col items-center justify-center gap-1 rounded-md border border-dashed border-neutral-400 bg-neutral-25 text-label text-neutral-500 hover:bg-neutral-50 disabled:cursor-not-allowed"
            >
              <ImagePlus className="size-6" aria-hidden="true" />
              이미지 추가
            </button>
          </li>
        )}
      </ul>
      <input
        ref={inputRef}
        type="file"
        multiple
        accept={PRODUCT_IMAGE_TYPES.join(',')}
        className="sr-only"
        aria-label="상품 이미지 파일"
        tabIndex={-1}
        onChange={(event) => void handleFiles(event)}
      />
      {message ? (
        <Notice tone="error">{message}</Notice>
      ) : (
        <p className="text-caption text-neutral-500">JPG, PNG, WEBP, GIF · 한 장에 10MB까지. 화살표로 순서를 바꿀 수 있어요.</p>
      )}
    </div>
  );
}

function MoveButton({ label, disabled, onClick, children }: { label: string; disabled: boolean; onClick: () => void; children: ReactNode }) {
  return (
    <button
      type="button"
      aria-label={label}
      disabled={disabled}
      onClick={onClick}
      className={cn('flex size-7 items-center justify-center rounded-full bg-neutral-900/70 text-neutral-0 hover:bg-neutral-900', disabled && 'invisible')}
    >
      {children}
    </button>
  );
}
