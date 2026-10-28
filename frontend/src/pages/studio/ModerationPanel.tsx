import { useMutation } from '@tanstack/react-query';
import { EyeOff, ShieldAlert, ShieldCheck, Undo2, X } from 'lucide-react';
import { useState } from 'react';
import { api, call, errorMessage, type ModerationAlert, type Schema } from '@/api';
import { Badge, Button, Card, CardBody, CardHeader, CardTitle, Notice, Select } from '@/components/ui';
import { formatDateTime } from '@/lib/format';

type LiveStream = Schema<'LiveStreamResponse'>;
type ChatModerationMode = NonNullable<LiveStream['chatModerationMode']>;

/** 채팅 AI 검토 처리 방식 (서버 ChatModerationMode) */
const CHAT_MODERATION_MODE_OPTIONS: { value: ChatModerationMode; label: string; hint: string }[] = [
  { value: 'ALERT', label: '알림만 받기', hint: '문제가 있어 보이는 채팅을 여기에 알려 드려요. 가릴지는 직접 판단해요.' },
  { value: 'AUTO_HIDE', label: '명백한 위반은 자동으로 가리기', hint: '돌려 적은 연락처·욕설처럼 명백한 채팅은 바로 가리고 알려 드려요. 잘못 가려졌으면 되돌릴 수 있어요.' },
  { value: 'OFF', label: 'AI 검토 끄기', hint: '연락처·외부 링크·욕설 같은 기본 규칙만 적용해요.' },
];

type Props = {
  liveStream: LiveStream & { id: number };
  alerts: ModerationAlert[];
  onDismiss: (messageId: string) => void;
  onSaved: (liveStream: LiveStream) => void;
};

/**
 * AI 검토 알림 (/user/queue/moderation): AI가 확인이 필요하다고 본 채팅·선물 한마디. 최신 알림이 위에 온다.
 * 위쪽에서 이 방송의 처리 방식(알림만 / 자동 가림 / 끄기)을 고른다. (PATCH /live-streams/{id}의 chatModerationMode)
 * 자동으로 가려진 메시지는 [되돌리기]로 시청자 화면에 다시 보이게 할 수 있다. [X]는 목록에서 지우기만 한다.
 */
export function ModerationPanel({ liveStream, alerts, onDismiss, onSaved }: Props) {
  const mode = liveStream.chatModerationMode ?? 'ALERT';
  const modeOption = CHAT_MODERATION_MODE_OPTIONS.find((option) => option.value === mode);

  const saveMode = useMutation({
    mutationFn: (chatModerationMode: ChatModerationMode) =>
      call(api.PATCH('/api/v1/live-streams/{liveStreamId}', { params: { path: { liveStreamId: liveStream.id } }, body: { chatModerationMode } })),
    onSuccess: (saved) => {
      if (saved) onSaved(saved);
    },
  });

  // 되돌린 메시지. 시청자 화면에는 MESSAGE_RESTORED로 반영되고, 여기서는 [되돌리기] 대신 "되돌림"을 보여 준다.
  const [restoredIds, setRestoredIds] = useState<ReadonlySet<string>>(new Set());
  const restore = useMutation({
    mutationFn: (messageId: string) =>
      call(
        api.POST('/api/v1/live-streams/{liveStreamId}/chat/messages/{messageId}/restore', {
          params: { path: { liveStreamId: liveStream.id, messageId } },
        }),
      ),
    onSuccess: (_, messageId) => setRestoredIds((prev) => new Set(prev).add(messageId)),
  });

  return (
    <Card aria-labelledby="moderation-title">
      <CardHeader>
        <CardTitle id="moderation-title" className="flex items-center gap-2 text-body-lg">
          AI 검토 알림
          {alerts.length > 0 && (
            <Badge variant="warning">
              <span className="num">{alerts.length}</span>건
            </Badge>
          )}
        </CardTitle>
      </CardHeader>
      <CardBody className="flex flex-col gap-3">
        <Select
          label="AI가 문제 채팅을 찾으면"
          options={CHAT_MODERATION_MODE_OPTIONS}
          value={mode}
          disabled={saveMode.isPending}
          onChange={(e) => saveMode.mutate(e.target.value as ChatModerationMode)}
        />
        {modeOption && <p className="-mt-1 text-caption text-neutral-500">{modeOption.hint}</p>}
        {saveMode.isError && <Notice tone="error">{errorMessage(saveMode.error)}</Notice>}
        {restore.isError && <Notice tone="error">{errorMessage(restore.error)}</Notice>}

        <ul aria-live="polite" aria-label="검토가 필요한 채팅" className="flex max-h-[260px] flex-col gap-2 overflow-y-auto">
          {alerts.length === 0 ? (
            <li className="flex items-center gap-2.5 rounded-md bg-neutral-25 px-3 py-3 text-label text-neutral-500">
              <ShieldCheck className="size-5 shrink-0 text-neutral-400" aria-hidden="true" />
              {mode === 'OFF' ? 'AI 검토를 껐어요. 기본 규칙에 걸린 채팅만 막아요.' : '외부 거래 유도, 욕설 같은 채팅이 오면 여기에 알려 드려요.'}
            </li>
          ) : (
            alerts.map((alert) => {
              const restored = restoredIds.has(alert.messageId);
              return (
                <li key={alert.messageId} className="flex flex-col gap-1.5 rounded-md border border-brand-100 bg-brand-50 px-3 py-2.5">
                  <div className="flex items-center justify-between gap-2">
                    <span className="flex min-w-0 items-center gap-1.5">
                      <ShieldAlert className="size-4 shrink-0 text-brand-700" aria-hidden="true" />
                      <Badge variant="live">{alert.categoryLabel || '확인 필요'}</Badge>
                      {alert.messageType === 'DONATION' && <Badge variant="neutral">선물 한마디</Badge>}
                      <strong className="truncate text-label font-semibold">{alert.nickname || '알 수 없음'}</strong>
                    </span>
                    <button
                      type="button"
                      aria-label={`${alert.nickname || '알 수 없음'}님 채팅 알림 확인`}
                      title="확인"
                      onClick={() => onDismiss(alert.messageId)}
                      className="flex size-7 shrink-0 items-center justify-center rounded-md text-neutral-500 hover:bg-brand-100 hover:text-neutral-900"
                    >
                      <X className="size-4" aria-hidden="true" />
                    </button>
                  </div>
                  <p className="text-body break-words">“{alert.content}”</p>
                  <p className="text-caption text-brand-700">{alert.reason}</p>
                  <div className="flex items-center justify-between gap-2">
                    {alert.detectedAt && <span className="text-caption text-neutral-500">{formatDateTime(alert.detectedAt)}</span>}
                    {alert.autoHidden &&
                      (restored ? (
                        <span className="text-caption text-neutral-500">시청자에게 다시 보여요</span>
                      ) : (
                        <span className="flex items-center gap-1.5">
                          <span className="flex items-center gap-1 text-caption font-semibold text-neutral-700">
                            <EyeOff className="size-3.5" aria-hidden="true" />
                            자동으로 가렸어요
                          </span>
                          <Button
                            variant="outline"
                            size="sm"
                            className="h-7 gap-1 px-2 text-caption"
                            disabled={restore.isPending}
                            aria-label={`${alert.nickname || '알 수 없음'}님 메시지 되돌리기`}
                            onClick={() => restore.mutate(alert.messageId)}
                          >
                            <Undo2 className="size-3.5" aria-hidden="true" />
                            되돌리기
                          </Button>
                        </span>
                      ))}
                  </div>
                </li>
              );
            })
          )}
        </ul>
      </CardBody>
    </Card>
  );
}
