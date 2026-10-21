import { ShieldAlert, ShieldCheck, X } from 'lucide-react';
import type { ModerationAlert } from '@/api';
import { Badge, Card, CardBody, CardHeader, CardTitle } from '@/components/ui';
import { formatDateTime } from '@/lib/format';

type Props = {
  alerts: ModerationAlert[];
  onDismiss: (messageId: string) => void;
};

/**
 * AI 검토 알림 (/user/queue/moderation): AI가 확인이 필요하다고 본 채팅. 최신 알림이 위에 온다.
 * 숨기기·채팅 금지 API가 아직 없어서 알림을 보여주고 [확인]으로 목록에서 지우기만 한다.
 */
export function ModerationPanel({ alerts, onDismiss }: Props) {
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
      <CardBody>
        <ul aria-live="polite" aria-label="검토가 필요한 채팅" className="flex max-h-[260px] flex-col gap-2 overflow-y-auto">
          {alerts.length === 0 ? (
            <li className="flex items-center gap-2.5 rounded-md bg-neutral-25 px-3 py-3 text-label text-neutral-500">
              <ShieldCheck className="size-5 shrink-0 text-neutral-400" aria-hidden="true" />
              외부 거래 유도, 욕설 같은 채팅이 오면 여기에 알려 드려요.
            </li>
          ) : (
            alerts.map((alert) => (
              <li key={alert.messageId} className="flex flex-col gap-1.5 rounded-md border border-brand-100 bg-brand-50 px-3 py-2.5">
                <div className="flex items-center justify-between gap-2">
                  <span className="flex min-w-0 items-center gap-1.5">
                    <ShieldAlert className="size-4 shrink-0 text-brand-700" aria-hidden="true" />
                    <Badge variant="live">{alert.categoryLabel || '확인 필요'}</Badge>
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
                {alert.detectedAt && <span className="text-caption text-neutral-500">{formatDateTime(alert.detectedAt)}</span>}
              </li>
            ))
          )}
        </ul>
      </CardBody>
    </Card>
  );
}
