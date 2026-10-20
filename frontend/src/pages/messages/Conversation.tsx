import { useInfiniteQuery, useMutation, useQuery, useQueryClient, type InfiniteData } from '@tanstack/react-query';
import { Flag, SendHorizontal } from 'lucide-react';
import { useEffect, useLayoutEffect, useRef, useState, type FormEvent, type KeyboardEvent } from 'react';
import { api, call, errorMessage, isErrorCode, useSession, type Schema } from '@/api';
import { Button, ConfirmDialog, EmptyState, ErrorState, Notice, Select, Skeleton, Textarea } from '@/components/ui';
import { cn } from '@/lib/cn';
import { DM_MAX_LENGTH, DM_REPORT_DETAIL_MAX_LENGTH, flattenMessages, isSendableDm, isUnreadByOther, lastMessageId, newClientMessageId, prependMessage, startsNewDay } from '@/lib/dm';
import { useDmEvent } from '@/lib/dm-events';
import { formatDate } from '@/lib/format';
import { DM_REPORT_REASON_LABEL, type DmReportReason } from '@/lib/labels';

type Message = Schema<'DmMessageResponse'>;
type MessagePage = Schema<'CursorResponseDmMessageResponse'> | undefined;

const PAGE_SIZE = 30;
/** 스크롤이 맨 아래에서 이만큼(px) 안에 있으면 새 메시지가 올 때 따라 내려간다. */
const STICK_TO_BOTTOM_PX = 64;

/** 보내는 중이거나 보내지 못한 메시지. 같은 clientMessageId로 다시 보내면 서버가 같은 메시지로 알아본다. */
type Pending = { clientMessageId: string; content: string; failed?: string };

/** 한 상대와의 대화: 메시지(이전 메시지 더 보기), 보내기, 읽음 표시("1"), 차단·신고 */
export function Conversation({ otherUserId }: { otherUserId: number }) {
  const session = useSession();
  const myId = session?.user.userId;

  // 상대와의 대화방을 가져온다. 없으면 서버가 만든다. (여러 번 불러도 같은 방)
  const room = useQuery({
    queryKey: ['dm', 'room', otherUserId],
    queryFn: () => call(api.POST('/api/v1/dm/rooms', { body: { targetUserId: otherUserId } })),
    staleTime: Infinity,
    retry: false,
  });

  if (room.isPending) {
    return (
      <div className="flex flex-1 flex-col gap-3 p-5" aria-busy="true">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-16 w-2/3" />
        <Skeleton className="ml-auto h-16 w-1/2" />
      </div>
    );
  }
  if (room.isError) {
    if (isErrorCode(room.error, 'DM_003')) return <EmptyState title="나에게는 메시지를 보낼 수 없어요" />;
    if (isErrorCode(room.error, 'DM_004')) return <EmptyState title="대화 상대를 찾을 수 없어요" description="탈퇴했거나 없는 회원이에요." />;
    return <ErrorState message={errorMessage(room.error)} onRetry={() => void room.refetch()} />;
  }
  if (!room.data?.roomId) return <EmptyState title="대화방을 열지 못했어요" />;

  return <RoomView room={room.data as Schema<'DmRoomResponse'> & { roomId: number }} myId={myId} />;
}

function RoomView({ room, myId }: { room: Schema<'DmRoomResponse'> & { roomId: number }; myId?: number }) {
  const roomId = room.roomId;
  const other = room.otherUser;
  const otherName = other?.nickname || '알 수 없음';
  const queryClient = useQueryClient();
  const messagesKey = ['dm', 'messages', roomId];

  const [otherLastRead, setOtherLastRead] = useState(room.otherLastReadMessageId ?? 0);
  const [blockedByMe, setBlockedByMe] = useState(room.blockedByMe ?? false);
  const [pending, setPending] = useState<Pending[]>([]);
  const [draft, setDraft] = useState('');
  const [reporting, setReporting] = useState<{ message?: Message }>();
  const [confirmBlock, setConfirmBlock] = useState(false);

  const query = useInfiniteQuery({
    queryKey: messagesKey,
    queryFn: ({ pageParam }) => call(api.GET('/api/v1/dm/rooms/{roomId}/messages', { params: { path: { roomId }, query: { cursor: pageParam, size: PAGE_SIZE } } })),
    initialPageParam: undefined as number | undefined,
    getNextPageParam: (last) => (last?.hasNext ? last.nextCursor : undefined),
  });
  const messages = flattenMessages(query.data?.pages.map((page) => page?.content) ?? []);

  /** 받은·보낸 메시지를 최신 페이지 맨 앞에 넣는다. */
  const addMessage = (message: Message) => {
    queryClient.setQueryData<InfiniteData<MessagePage, number | undefined>>(messagesKey, (data) => {
      if (!data) return data;
      const [first, ...rest] = data.pages;
      return { ...data, pages: [{ ...first, content: prependMessage(first?.content ?? [], message) }, ...rest] };
    });
  };

  useDmEvent((event) => {
    if (event.roomId !== roomId) return;
    if (event.type === 'MESSAGE' && event.message) {
      addMessage(event.message);
      setPending((list) => list.filter((p) => p.clientMessageId !== event.message?.clientMessageId));
    }
    if (event.type === 'READ' && event.readerId === other?.userId && event.lastReadMessageId) {
      setOtherLastRead((prev) => Math.max(prev, event.lastReadMessageId ?? 0));
    }
  });

  // 상대가 보낸 메시지가 화면에 들어오면 읽음 처리한다. (창이 보이고 있을 때만)
  const lastFromOther = lastMessageId(messages.filter((m) => m.senderId !== myId));
  const readSentRef = useRef(0);
  useEffect(() => {
    if (!lastFromOther || lastFromOther <= readSentRef.current) return;
    const markRead = () => {
      if (document.visibilityState !== 'visible' || lastFromOther <= readSentRef.current) return;
      readSentRef.current = lastFromOther;
      void call(api.POST('/api/v1/dm/rooms/{roomId}/read', { params: { path: { roomId } }, body: { lastReadMessageId: lastFromOther } }))
        .then(() => {
          void queryClient.invalidateQueries({ queryKey: ['dm', 'unread'] });
          void queryClient.invalidateQueries({ queryKey: ['dm', 'rooms'] });
        })
        .catch(() => {
          readSentRef.current = 0; // 다음에 다시 시도한다.
        });
    };
    markRead();
    document.addEventListener('visibilitychange', markRead);
    return () => document.removeEventListener('visibilitychange', markRead);
  }, [lastFromOther, roomId, queryClient]);

  const send = useMutation({
    mutationFn: (item: Pending) =>
      call(api.POST('/api/v1/dm/rooms/{roomId}/messages', { params: { path: { roomId } }, body: { clientMessageId: item.clientMessageId, content: item.content } })),
    onSuccess: (message, item) => {
      if (message) addMessage(message);
      setPending((list) => list.filter((p) => p.clientMessageId !== item.clientMessageId));
      void queryClient.invalidateQueries({ queryKey: ['dm', 'rooms'] });
    },
    onError: (error, item) => {
      setPending((list) => list.map((p) => (p.clientMessageId === item.clientMessageId ? { ...p, failed: errorMessage(error) } : p)));
    },
  });

  const submit = () => {
    if (blockedByMe || !isSendableDm(draft)) return;
    const item = { clientMessageId: newClientMessageId(), content: draft.trim() };
    setPending((list) => [...list, item]);
    setDraft('');
    stickRef.current = true;
    send.mutate(item);
  };
  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    submit();
  };
  const onKeyDown = (e: KeyboardEvent<HTMLTextAreaElement>) => {
    // Enter로 보내고 Shift+Enter로 줄을 바꾼다. 한글 조합 중 Enter는 보내지 않는다.
    if (e.key === 'Enter' && !e.shiftKey && !e.nativeEvent.isComposing) {
      e.preventDefault();
      submit();
    }
  };

  const block = useMutation({
    mutationFn: () =>
      blockedByMe
        ? call(api.DELETE('/api/v1/dm/blocks/{userId}', { params: { path: { userId: other?.userId ?? 0 } } }))
        : call(api.POST('/api/v1/dm/blocks', { body: { userId: other?.userId ?? 0 } })),
    onSuccess: () => {
      setBlockedByMe(!blockedByMe);
      setConfirmBlock(false);
      void queryClient.invalidateQueries({ queryKey: ['dm', 'blocks'] });
      void queryClient.invalidateQueries({ queryKey: ['dm', 'rooms'] });
      queryClient.setQueryData(['dm', 'room', other?.userId], { ...room, blockedByMe: !blockedByMe });
    },
  });

  // 스크롤: 새 메시지는 맨 아래를 보고 있을 때만 따라 내려가고, 이전 메시지를 불러오면 보던 자리를 지킨다.
  const listRef = useRef<HTMLDivElement>(null);
  const stickRef = useRef(true);
  const heightBeforeOlderRef = useRef<number | null>(null);
  const firstId = messages[0]?.messageId;
  useLayoutEffect(() => {
    const list = listRef.current;
    if (!list) return;
    if (heightBeforeOlderRef.current !== null) {
      list.scrollTop = list.scrollHeight - heightBeforeOlderRef.current;
      heightBeforeOlderRef.current = null;
    } else if (stickRef.current) {
      list.scrollTop = list.scrollHeight;
    }
  }, [messages.length, pending.length, firstId]);

  const loadOlder = () => {
    heightBeforeOlderRef.current = listRef.current?.scrollHeight ?? null;
    void query.fetchNextPage();
  };

  return (
    <>
      <div className="flex items-center justify-between gap-3 border-b border-neutral-100 px-5 py-3">
        <div className="flex items-center gap-3">
          <span aria-hidden="true" className="flex size-9 items-center justify-center rounded-full bg-brand-100 font-bold text-brand-700">
            {otherName.slice(0, 1)}
          </span>
          <h2 className="text-body-lg font-bold">{otherName}</h2>
        </div>
        <div className="flex gap-1">
          <Button variant="ghost" size="sm" onClick={() => setReporting({})}>
            신고
          </Button>
          <Button variant="ghost" size="sm" onClick={() => setConfirmBlock(true)}>
            {blockedByMe ? '차단 해제' : '차단'}
          </Button>
        </div>
      </div>

      <div
        ref={listRef}
        onScroll={(e) => {
          const el = e.currentTarget;
          stickRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < STICK_TO_BOTTOM_PX;
        }}
        className="min-h-0 flex-1 overflow-y-auto px-5 py-4"
      >
        {query.isPending ? (
          <div className="flex flex-col gap-3">
            <Skeleton className="h-12 w-2/3" />
            <Skeleton className="ml-auto h-12 w-1/2" />
          </div>
        ) : query.isError ? (
          <ErrorState message={errorMessage(query.error)} onRetry={() => void query.refetch()} />
        ) : (
          <>
            {query.hasNextPage && (
              <div className="mb-3 flex justify-center">
                <Button variant="outline" size="sm" loading={query.isFetchingNextPage} onClick={loadOlder}>
                  이전 메시지 보기
                </Button>
              </div>
            )}
            {messages.length === 0 && pending.length === 0 && (
              <p className="py-16 text-center text-body text-neutral-500">{otherName}님과 나눈 메시지가 없어요. 첫 메시지를 보내 보세요.</p>
            )}
            <ol aria-live="polite" aria-label={`${otherName}님과의 메시지`} className="flex flex-col gap-2">
              {messages.map((message, i) => (
                <MessageItem
                  key={message.messageId}
                  message={message}
                  mine={message.senderId === myId}
                  unread={isUnreadByOther(message, myId, otherLastRead)}
                  newDay={startsNewDay(messages[i - 1], message)}
                  onReport={() => setReporting({ message })}
                />
              ))}
              {pending.map((item) => (
                <li key={item.clientMessageId} className="flex flex-col items-end gap-1">
                  <p className={cn('max-w-[70%] rounded-lg px-3.5 py-2 text-body break-words whitespace-pre-wrap text-neutral-0', item.failed ? 'bg-neutral-400' : 'bg-neutral-700')}>
                    {item.content}
                  </p>
                  {item.failed ? (
                    <span className="flex items-center gap-2 text-caption text-brand-700">
                      보내지 못했어요. {item.failed}
                      <button type="button" className="font-semibold underline" onClick={() => send.mutate({ clientMessageId: item.clientMessageId, content: item.content })}>
                        다시 보내기
                      </button>
                      <button type="button" className="underline" onClick={() => setPending((list) => list.filter((p) => p.clientMessageId !== item.clientMessageId))}>
                        지우기
                      </button>
                    </span>
                  ) : (
                    <span className="text-caption text-neutral-500">보내는 중</span>
                  )}
                </li>
              ))}
            </ol>
          </>
        )}
      </div>

      {blockedByMe ? (
        <Notice className="m-3">차단한 상대예요. 차단을 해제하면 다시 메시지를 보낼 수 있어요.</Notice>
      ) : (
        <form onSubmit={onSubmit} className="flex items-end gap-2 border-t border-neutral-100 p-3">
          <div className="min-w-0 flex-1">
            <Textarea
              label="메시지 입력"
              hideLabel
              rows={2}
              maxLength={DM_MAX_LENGTH}
              placeholder="메시지를 입력하세요. (Enter 보내기, Shift+Enter 줄바꿈)"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={onKeyDown}
              className="min-h-0 resize-none"
            />
          </div>
          <button
            type="submit"
            aria-label="메시지 보내기"
            disabled={!isSendableDm(draft)}
            className="flex size-11 shrink-0 items-center justify-center rounded-md bg-neutral-900 text-neutral-0 transition-colors hover:bg-neutral-700 disabled:cursor-not-allowed disabled:bg-neutral-400"
          >
            <SendHorizontal className="size-[18px]" aria-hidden="true" />
          </button>
        </form>
      )}

      <ConfirmDialog
        open={confirmBlock}
        title={blockedByMe ? `${otherName}님 차단을 해제할까요?` : `${otherName}님을 차단할까요?`}
        description={
          <div className="flex flex-col gap-2">
            <p>{blockedByMe ? '해제하면 다시 서로 메시지를 보낼 수 있어요.' : '차단하면 서로 메시지를 보낼 수 없어요. 지금까지의 대화는 남아요.'}</p>
            {block.isError && <Notice tone="error">{errorMessage(block.error)}</Notice>}
          </div>
        }
        confirmLabel={blockedByMe ? '차단 해제' : '차단'}
        loading={block.isPending}
        onConfirm={() => block.mutate()}
        onCancel={() => {
          setConfirmBlock(false);
          block.reset();
        }}
      />
      {reporting && <ReportDialog roomId={roomId} otherName={otherName} message={reporting.message} onClose={() => setReporting(undefined)} />}
    </>
  );
}

function MessageItem({ message, mine, unread, newDay, onReport }: { message: Message; mine: boolean; unread: boolean; newDay: boolean; onReport: () => void }) {
  const time = message.sentAt ? message.sentAt.slice(11, 16) : '';
  return (
    <>
      {newDay && message.sentAt && (
        <li aria-hidden="true" className="my-2 self-center rounded-full bg-neutral-50 px-3 py-1 text-caption text-neutral-500">
          {formatDate(message.sentAt)}
        </li>
      )}
      <li className={cn('group flex items-end gap-1.5', mine ? 'flex-row-reverse' : 'flex-row')}>
        <p className={cn('max-w-[70%] rounded-lg px-3.5 py-2 text-body break-words whitespace-pre-wrap', mine ? 'bg-neutral-900 text-neutral-0' : 'bg-neutral-50 text-neutral-900')}>
          {message.content}
        </p>
        <span className={cn('flex flex-col gap-0.5 text-caption', mine ? 'items-end' : 'items-start')}>
          {unread && (
            <span className="num font-bold text-brand-700" aria-label="상대가 아직 읽지 않음">
              1
            </span>
          )}
          <span className="num text-neutral-500">{time}</span>
        </span>
        {!mine && (
          <button
            type="button"
            aria-label="이 메시지 신고"
            title="신고"
            onClick={onReport}
            className="invisible flex size-7 items-center justify-center rounded-md text-neutral-400 group-hover:visible hover:bg-neutral-50 hover:text-brand-700 focus-visible:visible"
          >
            <Flag className="size-3.5" aria-hidden="true" />
          </button>
        )}
      </li>
    </>
  );
}

const REASON_OPTIONS = (Object.entries(DM_REPORT_REASON_LABEL) as [DmReportReason, string][]).map(([value, label]) => ({ value, label }));

/** 신고: 메시지를 골랐으면 그 메시지를, 아니면 상대를 신고한다. 관리자가 신고된 대화를 확인한다. */
function ReportDialog({ roomId, otherName, message, onClose }: { roomId: number; otherName: string; message?: Message; onClose: () => void }) {
  const [reason, setReason] = useState<DmReportReason>('EXTERNAL_TRADE');
  const [detail, setDetail] = useState('');
  const [done, setDone] = useState(false);
  const report = useMutation({
    mutationFn: () => call(api.POST('/api/v1/dm/rooms/{roomId}/reports', { params: { path: { roomId } }, body: { messageId: message?.messageId, reason, detail: detail.trim() || undefined } })),
    onSuccess: () => setDone(true),
  });

  if (done) {
    return (
      <ConfirmDialog
        open
        title="신고를 접수했어요"
        description="관리자가 대화를 확인하고 조치해요. 더 이상 대화하고 싶지 않으면 차단할 수 있어요."
        confirmLabel="확인"
        cancelLabel="닫기"
        onConfirm={onClose}
        onCancel={onClose}
      />
    );
  }

  return (
    <ConfirmDialog
      open
      title={message ? '이 메시지를 신고할까요?' : `${otherName}님을 신고할까요?`}
      description={
        <div className="flex flex-col gap-3">
          {message && <p className="rounded-md bg-neutral-50 px-3 py-2 text-label break-words">“{message.content}”</p>}
          <Select label="신고 이유" options={REASON_OPTIONS} value={reason} onChange={(e) => setReason(e.target.value as DmReportReason)} />
          <Textarea label="자세한 내용 (선택)" rows={3} maxLength={DM_REPORT_DETAIL_MAX_LENGTH} value={detail} onChange={(e) => setDetail(e.target.value)} />
          {report.isError && <Notice tone="error">{errorMessage(report.error)}</Notice>}
        </div>
      }
      confirmLabel="신고"
      loading={report.isPending}
      onConfirm={() => report.mutate()}
      onCancel={onClose}
    />
  );
}
