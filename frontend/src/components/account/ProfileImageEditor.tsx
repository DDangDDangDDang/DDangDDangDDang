import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useRef, useState, type ChangeEvent } from 'react';
import { api, call, errorMessage, uploadToSignedUrl } from '@/api';
import { SellerAvatar } from '@/components/seller/SellerAvatar';
import { Button, Notice } from '@/components/ui';
import { PROFILE_IMAGE_TYPES, profileImageProblem } from '@/lib/profile-image';
import { myInfoQueryKey, type MyInfo } from './my-info-query';

/**
 * 프로필 사진 변경: 파일 고르기 → 업로드 URL 받기 → 저장소(S3)에 바로 올리기 → 서버에 확정.
 * 사진이 없으면 닉네임 첫 글자로 보여 준다.
 */
export function ProfileImageEditor({ nickname, imageUrl }: { nickname?: string; imageUrl?: string }) {
  const queryClient = useQueryClient();
  const inputRef = useRef<HTMLInputElement>(null);
  const [problem, setProblem] = useState<string>();

  const upload = useMutation({
    mutationFn: async (file: File) => {
      const signed = await call(api.POST('/api/v1/users/me/profile-image/upload-url', { body: { contentType: file.type, contentLength: file.size } }));
      if (!signed?.uploadUrl || !signed.imageKey) throw new Error('업로드 URL을 받지 못했어요.');
      await uploadToSignedUrl(signed.uploadUrl, file);
      return call(api.PUT('/api/v1/users/me/profile-image', { body: { imageKey: signed.imageKey } }));
    },
    onSuccess: (data) => queryClient.setQueryData<MyInfo>(myInfoQueryKey, data),
  });

  const remove = useMutation({
    mutationFn: () => call(api.DELETE('/api/v1/users/me/profile-image')),
    onSuccess: (data) => queryClient.setQueryData<MyInfo>(myInfoQueryKey, data),
  });

  function handleFile(event: ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    // 같은 파일을 다시 골라도 change가 일어나게 비운다.
    event.target.value = '';
    if (!file) return;
    const reason = profileImageProblem(file);
    setProblem(reason);
    upload.reset();
    remove.reset();
    if (!reason) upload.mutate(file);
  }

  const busy = upload.isPending || remove.isPending;
  const error = problem ?? (upload.isError ? errorMessage(upload.error) : remove.isError ? errorMessage(remove.error) : undefined);

  return (
    <div className="flex items-center gap-5">
      <SellerAvatar name={nickname} imageUrl={imageUrl} size="xl" />
      <div className="flex flex-col gap-2">
        <div className="flex items-center gap-2">
          <input
            ref={inputRef}
            type="file"
            accept={PROFILE_IMAGE_TYPES.join(',')}
            className="sr-only"
            aria-label="프로필 사진 파일"
            tabIndex={-1}
            onChange={handleFile}
          />
          <Button variant="outline" size="sm" loading={upload.isPending} disabled={busy} onClick={() => inputRef.current?.click()}>
            사진 변경
          </Button>
          {imageUrl && (
            <Button variant="ghost" size="sm" loading={remove.isPending} disabled={busy} onClick={() => remove.mutate()}>
              사진 삭제
            </Button>
          )}
        </div>
        <p className="text-caption text-neutral-500">JPG, PNG, WEBP, GIF · 5MB 이하</p>
        {error && <Notice tone="error">{error}</Notice>}
        {upload.isSuccess && <Notice tone="success">프로필 사진을 바꿨어요.</Notice>}
      </div>
    </div>
  );
}
