import { useQuery } from '@tanstack/react-query';
import { ExternalLink, RefreshCw } from 'lucide-react';
import { api, call, errorMessage, isErrorCode, type Schema } from '@/api';
import { Badge, Button, Notice, Skeleton } from '@/components/ui';
import { courierTrackingUrl, trackingStatusLabel, trackingStatusVariant } from '@/lib/delivery-tracking';
import { formatDateTime } from '@/lib/format';
import { COURIER_LABEL } from '@/lib/labels';

type Order = Schema<'OrderResponse'>;
type TrackingEvent = Schema<'TrackingEvent'>;

/**
 * 배송 조회: 현재 상태 + 이력(최신 순). 발송된 주문(택배사·송장번호 있음)에서만 보여준다.
 * 조회가 안 되면(아직 등록 전, 서비스 장애, 지원하지 않는 택배사) 택배사 조회 페이지 링크로 대신한다.
 */
export function TrackingCard({ order }: { order: Order }) {
    const orderId = order.orderId;
    const query = useQuery({
        queryKey: ['orders', 'tracking', orderId],
        queryFn: () => call(api.GET('/api/v1/orders/{orderId}/tracking', { params: { path: { orderId: orderId ?? 0 } } })),
        enabled: orderId != null && Boolean(order.trackingNo),
        // 서버가 10분 캐시하므로 화면도 자주 다시 묻지 않는다
        staleTime: 60_000,
        retry: false,
    });

    if (!order.trackingNo) return null;
    const officialUrl = courierTrackingUrl(order.courier, order.trackingNo);

    return (
        <section aria-label="배송 조회" className="flex flex-col gap-3 rounded-md border border-neutral-100 p-4">
            <div className="flex items-center justify-between gap-2">
                <h3 className="text-body font-bold">배송 조회</h3>
                <div className="flex items-center gap-1">
                    {officialUrl && (
                        <a href={officialUrl} target="_blank" rel="noopener noreferrer" className="inline-flex items-center gap-1 text-label text-neutral-500 hover:text-neutral-900">
                            {order.courier ? COURIER_LABEL[order.courier] : '택배사'}에서 보기
                            <ExternalLink className="size-3.5" aria-hidden="true" />
                        </a>
                    )}
                    <Button variant="ghost" size="icon" aria-label="배송 조회 새로고침" onClick={() => void query.refetch()} disabled={query.isFetching}>
                        <RefreshCw className="size-4" aria-hidden="true" />
                    </Button>
                </div>
            </div>

            {query.isPending ? (
                <div className="flex flex-col gap-2">
                    <Skeleton className="h-6 w-40" />
                    <Skeleton className="h-16" />
                </div>
            ) : query.isError ? (
                <Notice tone={isErrorCode(query.error, 'ORDER_014') ? 'info' : 'error'}>
                    {isErrorCode(query.error, 'ORDER_013')
                        ? '이 택배사는 배송 조회를 지원하지 않아요. 송장번호로 택배사에 문의해 주세요.'
                        : errorMessage(query.error)}
                    {officialUrl && !isErrorCode(query.error, 'ORDER_013') && ' 택배사 사이트에서 직접 확인할 수도 있어요.'}
                </Notice>
            ) : (
                <TrackingTimeline lastEvent={query.data?.lastEvent} events={query.data?.events ?? []} />
            )}
        </section>
    );
}

function TrackingTimeline({ lastEvent, events }: { lastEvent?: TrackingEvent; events: TrackingEvent[] }) {
    if (!lastEvent && events.length === 0) {
        return <p className="text-body text-neutral-500">아직 배송 이력이 없어요.</p>;
    }
    // 최신 이력이 위로 오게 뒤집는다 (서버는 오래된 순)
    const latestFirst = [...events].reverse();

    return (
        <div className="flex flex-col gap-3">
            {lastEvent && (
                <div className="flex flex-wrap items-center gap-2">
                    <Badge variant={trackingStatusVariant(lastEvent.statusCode)}>{trackingStatusLabel(lastEvent.statusCode)}</Badge>
                    <span className="text-body">{[lastEvent.location, lastEvent.statusName].filter(Boolean).join(' · ')}</span>
                    {lastEvent.time && <span className="text-caption text-neutral-500">{formatDateTime(lastEvent.time)}</span>}
                </div>
            )}
            {latestFirst.length > 0 && (
                <ol className="flex flex-col gap-2 border-l border-neutral-100 pl-4">
                    {latestFirst.map((event, index) => (
                        <li key={`${event.time}-${index}`} className="flex flex-col">
              <span className="text-body">
                <span className="font-semibold">{event.statusName ?? trackingStatusLabel(event.statusCode)}</span>
                  {event.location && <span className="text-neutral-500"> · {event.location}</span>}
              </span>
                            {event.description && <span className="text-label text-neutral-500">{event.description}</span>}
                            {event.time && <span className="text-caption text-neutral-500">{formatDateTime(event.time)}</span>}
                        </li>
                    ))}
                </ol>
            )}
        </div>
    );
}