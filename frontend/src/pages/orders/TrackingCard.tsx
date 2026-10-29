import { useQuery } from '@tanstack/react-query';
import { ClipboardList, ExternalLink, House, Package, RefreshCw, Truck, type LucideIcon } from 'lucide-react';
import { api, call, errorMessage, isErrorCode, type Schema } from '@/api';
import { Badge, Button, Notice, Skeleton } from '@/components/ui';
import { cn } from '@/lib/cn';
import { courierTrackingUrl, TRACKING_STEPS, trackingStatusLabel, trackingStatusVariant, trackingSteps } from '@/lib/delivery-tracking';
import { formatDateTime } from '@/lib/format';
import { COURIER_LABEL } from '@/lib/labels';

type Order = Schema<'OrderResponse'>;
type TrackingEvent = Schema<'TrackingEvent'>;

/**
 * 배송 조회: 현재 상태 + 단계 그림(접수 → 출발 → 배송 중 → 도착) + 최근 이력(최대 20개, 최신 순, 접어 둠). 발송된 주문(택배사·송장번호 있음)에서만 보여준다.
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

const STEP_ICONS: LucideIcon[] = [ClipboardList, Package, Truck, House];

function TrackingTimeline({ lastEvent, events }: { lastEvent?: TrackingEvent; events: TrackingEvent[] }) {
    if (!lastEvent && events.length === 0) {
        return <p className="text-body text-neutral-500">아직 배송 이력이 없어요.</p>;
    }
    // 최신 이력이 위로 오게 뒤집는다 (서버는 오래된 순)
    const latestFirst = [...events].reverse();
    // 현재 상태가 이력에 없으면(이력이 비었거나 최근 20개 밖) 단계 그림에 더한다.
    // 배달 완료 뒤에 이벤트가 더 와도 현재 상태는 배달 완료이므로, 마지막 이력이 아니라 이력 전체에서 찾는다
    const stepEvents =
        lastEvent && !events.some((event) => event.time === lastEvent.time && event.statusCode === lastEvent.statusCode)
            ? [...events, lastEvent]
            : events;

    return (
        <div className="flex flex-col gap-4">
            {lastEvent && (
                <div className="flex flex-wrap items-center gap-2">
                    <Badge variant={trackingStatusVariant(lastEvent.statusCode)}>{trackingStatusLabel(lastEvent.statusCode)}</Badge>
                    <span className="text-body">{[lastEvent.location, lastEvent.statusName].filter(Boolean).join(' · ')}</span>
                    {lastEvent.time && <span className="text-caption text-neutral-500">{formatDateTime(lastEvent.time)}</span>}
                </div>
            )}
            <TrackingSteps events={stepEvents} problem={trackingStatusVariant(lastEvent?.statusCode) === 'warning'} />
            {latestFirst.length > 0 && (
                <details>
                    <summary className="cursor-pointer text-label text-neutral-500 hover:text-neutral-900">최근 이력 보기 ({latestFirst.length})</summary>
                    <ol aria-label="배송 이력" className="mt-3 flex flex-col gap-2 border-l border-neutral-100 pl-4">
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
                </details>
            )}
        </div>
    );
}

/**
 * 접수 → 출발 → 배송 중 → 도착. 지나온 단계와 지금 단계는 색을 채우고, 단계 아래에 택배사 상태 이름을 작게 적는다.
 * 배달 실패·문제가 있으면 지금 단계를 경고 색으로 칠한다. (글자로도 '배달 실패' 배지가 위에 있다)
 */
function TrackingSteps({ events, problem }: { events: TrackingEvent[]; problem: boolean }) {
    const { current, details } = trackingSteps(events);

    return (
        <ol aria-label="배송 단계" className="grid grid-cols-4">
            {TRACKING_STEPS.map((label, index) => {
                const Icon = STEP_ICONS[index] ?? Package;
                const reached = index <= current;
                const isCurrent = index === current;
                return (
                    <li key={label} aria-current={isCurrent ? 'step' : undefined} className="relative flex flex-col items-center gap-1.5 px-1 text-center">
                        {index < TRACKING_STEPS.length - 1 && (
                            <span
                                aria-hidden="true"
                                className={cn('absolute top-5 left-1/2 h-0.5 w-full', index < current ? 'bg-info-600' : 'bg-neutral-200')}
                            />
                        )}
                        <span
                            className={cn(
                                'relative flex size-10 items-center justify-center rounded-full border-2',
                                !reached && 'border-neutral-200 bg-neutral-0 text-neutral-400',
                                reached && !isCurrent && 'border-info-600 bg-info-50 text-info-700',
                                isCurrent && !problem && 'border-info-600 bg-info-600 text-neutral-25',
                                isCurrent && problem && 'border-red-500 bg-red-500 text-white',
                            )}
                        >
                            <Icon className="size-5" aria-hidden="true" />
                        </span>
                        <span className={cn('text-label', isCurrent ? 'font-bold text-neutral-900' : reached ? 'text-neutral-700' : 'text-neutral-500')}>
                            {label}
                            {reached && !isCurrent && <span className="sr-only"> (지남)</span>}
                            {isCurrent && <span className="sr-only"> (현재)</span>}
                        </span>
                        {(details[index]?.length ?? 0) > 0 && (
                            <span className="text-caption break-keep text-neutral-500">{details[index]?.join(' · ')}</span>
                        )}
                    </li>
                );
            })}
        </ol>
    );
}
