import { ImagePlus, X } from 'lucide-react';
import { useRef, useState, type ChangeEvent } from 'react';
import { errorMessage } from '@/api';
import { Button, Notice, Textarea } from '@/components/ui';
import {
  DESCRIPTION_IMAGE_MAX_COUNT,
  descriptionImageKeys,
  descriptionTextLength,
  insertImage,
  parseDescription,
  removeImage,
  serializeDescription,
  type DescriptionBlock,
} from '@/lib/product-description';
import { PRODUCT_DESCRIPTION_MAX_LENGTH, type ProductFormChange } from '@/lib/product-form';
import { PRODUCT_IMAGE_TYPES, productImageProblem } from '@/lib/product-image';
import { useProductImageUpload } from './use-product-image-upload';

type Props = {
  description: string;
  imageUrls: Record<string, string>;
  onChange: (change: ProductFormChange) => void;
  error?: string;
};

/**
 * 상품 설명 편집: 글 사이에 이미지를 넣는다. 마지막으로 입력하던 칸의 커서 자리(없으면 맨 끝)에 이미지가 들어간다.
 * 저장은 글자 그대로 + 이미지 자리는 "![](키)" 한 줄. (@/lib/product-description)
 */
export function ProductDescriptionEditor({ description, imageUrls, onChange, error }: Props) {
  const blocks = parseDescription(description);
  const inputRef = useRef<HTMLInputElement>(null);
  // 이미지를 넣을 자리: 마지막으로 입력하던 글 칸과 커서 위치
  const caret = useRef<{ index: number; position: number }>(undefined);
  const [problem, setProblem] = useState<string>();

  const upload = useProductImageUpload((image) =>
    onChange((prev) => {
      const current = parseDescription(prev.description);
      const at = caret.current ?? { index: current.length - 1, position: Number.MAX_SAFE_INTEGER };
      caret.current = undefined;
      return {
        description: serializeDescription(insertImage(current, at.index, at.position, image.key)),
        descriptionImageUrls: { ...prev.descriptionImageUrls, [image.key]: image.url },
      };
    }),
  );

  const update = (next: DescriptionBlock[]) => onChange({ description: serializeDescription(next) });
  const imageCount = descriptionImageKeys(description).length;
  const full = imageCount >= DESCRIPTION_IMAGE_MAX_COUNT;

  function handleFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    event.target.value = '';
    if (!file) return;
    const reason = productImageProblem(file);
    setProblem(reason);
    upload.reset();
    if (!reason) upload.mutate(file);
  }

  const textBlockCount = blocks.filter((block) => block.type === 'text').length;
  const message = problem ?? (upload.isError ? errorMessage(upload.error) : undefined);

  return (
    <div className="flex flex-col gap-2">
      {blocks.map((block, index) => {
        if (block.type === 'image') {
          const url = imageUrls[block.key];
          return (
            <figure key={`image-${index}`} className="relative self-start overflow-hidden rounded-md border border-neutral-200 bg-neutral-50">
              {url ? (
                <img src={url} alt="설명 속 이미지" className="max-h-72 max-w-full object-contain" />
              ) : (
                <div className="flex h-24 w-60 items-center justify-center text-label text-neutral-500">이미지를 불러올 수 없어요</div>
              )}
              <button
                type="button"
                aria-label="설명에서 이미지 빼기"
                onClick={() => update(removeImage(blocks, index))}
                className="absolute top-1.5 right-1.5 flex size-7 items-center justify-center rounded-full bg-neutral-900/70 text-neutral-0 hover:bg-neutral-900"
              >
                <X className="size-4" aria-hidden="true" />
              </button>
            </figure>
          );
        }
        const first = index === 0;
        const last = index === blocks.length - 1;
        return (
          <Textarea
            key={`text-${index}`}
            label={first ? '상품 설명' : '상품 설명 (이어서)'}
            hideLabel={!first}
            rows={textBlockCount === 1 ? 5 : Math.max(2, block.text.split('\n').length)}
            placeholder={first ? '상태, 구성품, 사이즈 등을 적어 주세요. 사진은 아래 [설명에 이미지 넣기]로 글 사이에 넣을 수 있어요.' : undefined}
            value={block.text}
            onChange={(e) => update(blocks.map((item, i) => (i === index ? { type: 'text', text: e.target.value } : item)))}
            onSelect={(e) => {
              caret.current = { index, position: e.currentTarget.selectionStart };
            }}
            error={last ? error : undefined}
            hint={
              last ? (
                <span>
                  <span className="num">{descriptionTextLength(description)}</span>/{PRODUCT_DESCRIPTION_MAX_LENGTH}자 · 이미지 <span className="num">{imageCount}</span>/
                  {DESCRIPTION_IMAGE_MAX_COUNT}장
                </span>
              ) : undefined
            }
          />
        );
      })}
      <div className="flex items-center gap-3">
        <Button type="button" variant="outline" size="sm" disabled={full} loading={upload.isPending} onClick={() => inputRef.current?.click()}>
          <ImagePlus className="size-4" aria-hidden="true" />
          설명에 이미지 넣기
        </Button>
        <span className="text-caption text-neutral-500">마지막으로 입력하던 자리에 들어가요.</span>
      </div>
      <input
        ref={inputRef}
        type="file"
        accept={PRODUCT_IMAGE_TYPES.join(',')}
        className="sr-only"
        aria-label="설명에 넣을 이미지 파일"
        tabIndex={-1}
        onChange={handleFile}
      />
      {message && <Notice tone="error">{message}</Notice>}
    </div>
  );
}
