import { useMutation } from '@tanstack/react-query';
import { Megaphone } from 'lucide-react';
import { useState, type FormEvent } from 'react';
import { api, call, errorMessage, type Schema } from '@/api';
import { Button, Card, CardBody, CardHeader, CardTitle, Notice, Textarea } from '@/components/ui';

type LiveStream = Schema<'LiveStreamResponse'>;

/** 서버와 같은 공지 길이 제한 (LiveStreamUpdateRequest.notice) */
export const NOTICE_MAX_LENGTH = 200;

/**
 * 채팅 공지: 판매자가 채팅창 위에 고정할 글을 올리거나 내린다. (PATCH /live-streams/{id}의 notice)
 * 시청자는 [X]로 닫을 수 있지만 다시 들어오면 다시 보인다. 바뀐 공지는 방송 정보 변경 알림으로 시청 화면에 바로 반영된다.
 */
export function ChatNoticeEditor({ liveStream, onSaved }: { liveStream: LiveStream & { id: number }; onSaved: (liveStream: LiveStream) => void }) {
  const current = liveStream.notice ?? '';
  const [draft, setDraft] = useState(current);
  // 다른 탭에서 공지를 바꾸면 입력도 새 공지로 맞춘다.
  const [prevCurrent, setPrevCurrent] = useState(current);
  if (prevCurrent !== current) {
    setPrevCurrent(current);
    setDraft(current);
  }

  const save = useMutation({
    mutationFn: (notice: string) =>
      call(api.PATCH('/api/v1/live-streams/{liveStreamId}', { params: { path: { liveStreamId: liveStream.id } }, body: { notice } })),
    onSuccess: (saved) => {
      if (saved) onSaved(saved);
    },
  });

  const trimmed = draft.trim();
  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (!trimmed || trimmed === current) return;
    save.mutate(trimmed);
  };

  return (
    <Card aria-labelledby="chat-notice-title">
      <CardHeader>
        <CardTitle id="chat-notice-title" className="flex items-center gap-2 text-body-lg">
          <Megaphone className="size-4 text-brand-700" aria-hidden="true" />
          채팅 공지
        </CardTitle>
      </CardHeader>
      <CardBody>
        <form onSubmit={onSubmit} className="flex flex-col gap-3">
          <Textarea
            label="채팅창 위에 고정할 공지"
            hideLabel
            placeholder="예) 오늘 9시에 한정판 경매가 시작돼요"
            value={draft}
            maxLength={NOTICE_MAX_LENGTH}
            onChange={(e) => setDraft(e.target.value)}
            className="min-h-16"
            hint={
              <span>
                시청자가 닫아도 다시 들어오면 보여요. 연락처·외부 링크는 적을 수 없어요.{' '}
                <span className="num">
                  {draft.length}/{NOTICE_MAX_LENGTH}
                </span>
              </span>
            }
          />
          {save.isError && <Notice tone="error">{errorMessage(save.error)}</Notice>}
          <div className="flex justify-end gap-2">
            {current && (
              <Button type="button" variant="outline" size="sm" disabled={save.isPending} onClick={() => save.mutate('')}>
                공지 내리기
              </Button>
            )}
            <Button type="submit" size="sm" loading={save.isPending} disabled={!trimmed || trimmed === current}>
              {current ? '공지 바꾸기' : '공지 올리기'}
            </Button>
          </div>
        </form>
      </CardBody>
    </Card>
  );
}
