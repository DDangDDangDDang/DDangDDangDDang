import { useMutation, useQueryClient } from '@tanstack/react-query';
import { useState, type FormEvent } from 'react';
import { api, call, errorMessage, replaceSession } from '@/api';
import { Button, Notice, Textarea } from '@/components/ui';
import { myInfoQueryKey, type MyInfo } from './my-info-query';

/** 서버와 같은 길이 제한 (User.BIO_MAX_LENGTH) */
const BIO_MAX = 150;

/** 자기소개. 판매자 프로필 창에 함께 보인다. 비우고 저장하면 지운다. */
export function BioEditor({ current }: { current?: string }) {
  const queryClient = useQueryClient();
  const [editing, setEditing] = useState(false);
  const [bio, setBio] = useState(current ?? '');

  const mutation = useMutation({
    mutationFn: (next: string) => call(api.PATCH('/api/v1/users/me', { body: { bio: next } })),
    onSuccess: (data) => {
      if (data?.accessToken && data.user) replaceSession(data.accessToken, data.user);
      queryClient.setQueryData<MyInfo>(myInfoQueryKey, data?.user);
      setEditing(false);
    },
  });

  if (!editing) {
    return (
      <div className="flex flex-col gap-2">
        <div className="flex items-start gap-3">
          <p className={current ? 'flex-1 whitespace-pre-line' : 'flex-1 text-neutral-500'}>{current || '아직 소개가 없어요.'}</p>
          <Button
            variant="outline"
            size="sm"
            onClick={() => {
              setBio(current ?? '');
              mutation.reset();
              setEditing(true);
            }}
          >
            {current ? '수정' : '작성'}
          </Button>
        </div>
        {mutation.isSuccess && <Notice tone="success">소개를 저장했어요.</Notice>}
      </div>
    );
  }

  function handleSubmit(event: FormEvent) {
    event.preventDefault();
    mutation.mutate(bio.trim());
  }

  return (
    <form onSubmit={handleSubmit} className="flex flex-col gap-2">
      <Textarea
        label="자기소개"
        hideLabel
        autoFocus
        value={bio}
        maxLength={BIO_MAX}
        placeholder="어떤 물건을 좋아하는지, 어떤 판매자인지 적어 보세요."
        hint={`${bio.length}/${BIO_MAX}자 · 연락처나 외부 링크는 적을 수 없어요.`}
        error={mutation.isError ? errorMessage(mutation.error) : undefined}
        onChange={(e) => setBio(e.target.value)}
      />
      <div className="flex justify-end gap-2">
        <Button variant="ghost" onClick={() => setEditing(false)} disabled={mutation.isPending}>
          취소
        </Button>
        <Button type="submit" loading={mutation.isPending}>
          저장
        </Button>
      </div>
    </form>
  );
}
