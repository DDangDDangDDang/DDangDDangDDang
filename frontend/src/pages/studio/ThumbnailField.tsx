import { useMutation } from '@tanstack/react-query';
import { ImageIcon } from 'lucide-react';
import { useEffect, useId, useRef, useState, type ChangeEvent } from 'react';
import { errorMessage, type Schema } from '@/api';
import { LiveThumbnail } from '@/components/live/LiveStreamCard';
import { Button, Notice } from '@/components/ui';
import { CUSTOM_THUMBNAIL_TYPES, customThumbnailProblem } from '@/lib/live-thumbnail';
import { deleteCustomThumbnail, uploadCustomThumbnail } from './custom-thumbnail';

type LiveStream = Schema<'LiveStreamResponse'>;

export const THUMBNAIL_HINT = 'JPG, PNG, WEBP · 2MB 이하 · 16:9 (1280×720) 권장';
const AUTO_HINT = '올리지 않으면 방송 중에 송출 화면을 1분마다 찍어 카드에 보여 줘요.';

type Props =
  /** 방송을 만들 때: 고른 파일을 들고 있다가 방송을 만든 뒤 올린다. (StreamForm) */
  | { mode: 'create'; file?: File; onFileChange: (file: File | undefined) => void; disabled?: boolean }
  /** 만든 방송: 고르면 바로 올리고, 지우면 바로 자동 캡처로 돌아간다. */
  | { mode: 'edit'; liveStream: LiveStream & { id: number }; onChange: (liveStream: LiveStream) => void };

/**
 * 방송 카드 썸네일 고르기·미리보기·지우기.
 * 판매자가 올린 이미지가 있으면 홈·방송 목록 카드에 그 이미지가 보이고, 없으면 방송 중 송출 화면 자동 캡처가 보인다.
 */
export function ThumbnailField(props: Props) {
  const labelId = useId();
  const inputRef = useRef<HTMLInputElement>(null);
  const [problem, setProblem] = useState<string>();
  const localPreview = useObjectUrl(props.mode === 'create' ? props.file : undefined);

  const liveStreamId = props.mode === 'edit' ? props.liveStream.id : 0;
  const onSaved = (saved: LiveStream | undefined) => {
    if (saved && props.mode === 'edit') props.onChange(saved);
  };
  const upload = useMutation({ mutationFn: (file: File) => uploadCustomThumbnail(liveStreamId, file), onSuccess: onSaved });
  const remove = useMutation({ mutationFn: () => deleteCustomThumbnail(liveStreamId), onSuccess: onSaved });

  const previewUrl = props.mode === 'create' ? localPreview : props.liveStream.customThumbnailUrl;
  const busy = upload.isPending || remove.isPending;
  const disabled = busy || (props.mode === 'create' && props.disabled);
  const error = problem ?? (upload.isError ? errorMessage(upload.error) : remove.isError ? errorMessage(remove.error) : undefined);

  function handleFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    // 같은 파일을 다시 골라도 change가 일어나게 비운다.
    event.target.value = '';
    if (!file) return;
    const reason = customThumbnailProblem(file);
    setProblem(reason);
    upload.reset();
    remove.reset();
    if (reason) return;
    if (props.mode === 'create') props.onFileChange(file);
    else upload.mutate(file);
  }

  function handleRemove() {
    setProblem(undefined);
    upload.reset();
    if (props.mode === 'create') props.onFileChange(undefined);
    else remove.mutate();
  }

  return (
    <div role="group" aria-labelledby={labelId} className="flex flex-col gap-2">
      <span id={labelId} className="text-label font-semibold text-neutral-900">
        방송 썸네일 (선택)
      </span>
      <div className="flex items-start gap-4">
        <LiveThumbnail name="" imageUrl={previewUrl} className="w-[240px] shrink-0 ring-1 ring-neutral-200">
          {!previewUrl && (
            <span className="flex flex-col items-center gap-1.5 text-caption text-neutral-500">
              <ImageIcon className="size-6" aria-hidden="true" />
              자동 캡처 사용 중
            </span>
          )}
        </LiveThumbnail>
        <div className="flex flex-col gap-2">
          <div className="flex items-center gap-2">
            <input
              ref={inputRef}
              type="file"
              accept={CUSTOM_THUMBNAIL_TYPES.join(',')}
              className="sr-only"
              aria-label="썸네일 이미지 파일"
              tabIndex={-1}
              onChange={handleFile}
            />
            <Button type="button" variant="outline" size="sm" loading={upload.isPending} disabled={disabled} onClick={() => inputRef.current?.click()}>
              {previewUrl ? '이미지 바꾸기' : '이미지 올리기'}
            </Button>
            {previewUrl && (
              <Button type="button" variant="ghost" size="sm" loading={remove.isPending} disabled={disabled} onClick={handleRemove}>
                지우기
              </Button>
            )}
          </div>
          <p className="text-caption text-neutral-500">{THUMBNAIL_HINT}</p>
          <p className="text-caption text-neutral-500">{AUTO_HINT}</p>
          {error && <Notice tone="error">{error}</Notice>}
          {props.mode === 'edit' && upload.isSuccess && <Notice tone="success">썸네일을 바꿨어요. 방송 카드에 바로 보여요.</Notice>}
          {props.mode === 'edit' && remove.isSuccess && <Notice tone="success">썸네일을 지웠어요. 방송 중 화면 자동 캡처로 돌아가요.</Notice>}
        </div>
      </div>
    </div>
  );
}

/** 고른 파일의 미리보기 주소. 파일이 바뀌거나 화면을 떠나면 주소를 돌려준다. */
function useObjectUrl(file: File | undefined): string | undefined {
  const [url, setUrl] = useState<string>();
  useEffect(() => {
    if (!file) {
      setUrl(undefined);
      return;
    }
    const next = URL.createObjectURL(file);
    setUrl(next);
    return () => URL.revokeObjectURL(next);
  }, [file]);
  return url;
}
