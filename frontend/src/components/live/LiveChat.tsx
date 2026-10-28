import { Gavel, HandCoins, Megaphone, SendHorizontal, X } from 'lucide-react';
import { useEffect, useLayoutEffect, useRef, useState, type FormEvent } from 'react';
import { useSession, type ChatMessage, type RealtimeStatus } from '@/api';
import { Badge, Notice } from '@/components/ui';
import { bidIncreasePercent, bidTier } from '@/lib/auction-effects';
import { cn } from '@/lib/cn';
import { formatMileage } from '@/lib/format';
import { CHAT_MAX_LENGTH, isSendableChat } from '@/lib/live-room';

type Props = {
  messages: ChatMessage[];
  sellerId?: number;
  /** 방송 중일 때만 보낼 수 있다. */
  canChat: boolean;
  status: RealtimeStatus;
  /** 채팅 전송 실패 안내 (/user/queue/errors) */
  error?: string;
  onSend: (content: string) => boolean;
  /** AI 검토 알림이 온 채팅의 messageId. 판매자 화면에서만 넘겨 해당 채팅에 표시한다. */
  flaggedIds?: ReadonlySet<string>;
  /** AI 검토로 가려진 메시지의 원래 내용을 보여 줄지. 판매자 화면만 true (시청자는 "가려진 메시지"만 본다) */
  showHiddenContent?: boolean;
  /**
   * 로그인하지 않은 사람이 보내려고 할 때 부른다. ("로그인이 필요합니다" 창, useLoginGuard)
   * 로그인하면 afterLogin으로 입력칸에 다시 포커스해 쓰던 채팅을 이어서 보낼 수 있게 한다.
   */
  requireLogin?: (afterLogin: () => void) => void;
  /** 판매자가 채팅창 위에 고정한 공지 (LiveStreamResponse.notice) */
  notice?: string;
};

/** 스크롤이 맨 아래에서 이만큼(px) 안에 있으면 새 메시지가 올 때 따라 내려간다. */
const STICK_TO_BOTTOM_PX = 48;

/** 실시간 채팅: 메시지 목록 + 입력. 메시지는 접속한 뒤부터 쌓인다. (이전 채팅 API 없음) 방송 시청·판매자 스튜디오가 같이 쓴다. */
export function LiveChat({ messages, sellerId, canChat, status, error, onSend, flaggedIds, showHiddenContent = false, requireLogin, notice }: Props) {
  const session = useSession();
  // [X]로 닫은 공지. 이 화면에서만 기억해서, 다시 들어오면(새로 그리면) 다시 보인다. 판매자가 공지를 바꾸면 새 공지는 보인다.
  const [dismissedNotice, setDismissedNotice] = useState<string>();
  // 판매자가 공지를 내렸다가 같은 문구로 다시 올리면 새 공지로 보고 다시 보인다.
  const [prevNotice, setPrevNotice] = useState(notice);
  if (prevNotice !== notice) {
    setPrevNotice(notice);
    if (!notice) setDismissedNotice(undefined);
  }
  const showNotice = notice && notice !== dismissedNotice;
  const [draft, setDraft] = useState('');
  const [notSent, setNotSent] = useState(false);
  const listRef = useRef<HTMLUListElement>(null);
  const stickRef = useRef(true);
  const inputRef = useRef<HTMLInputElement>(null);
  // 로그인하지 않았으면 채팅을 보기만 하고, 보내려고 하면 로그인 창을 띄운다. (쓰던 내용은 그대로 둔다)
  const guest = !session && requireLogin !== undefined;
  // 로그인하면 입력칸에 다시 포커스한다. 로그인 직후에는 채팅이 회원으로 다시 연결되느라 입력칸이 잠깐 꺼지므로, 켜진 뒤에 한다.
  const [focusAfterLogin, setFocusAfterLogin] = useState(false);

  // 사용자가 위로 올려 이전 채팅을 읽는 중이면 새 메시지가 와도 스크롤을 옮기지 않는다.
  useLayoutEffect(() => {
    const list = listRef.current;
    if (list && stickRef.current) list.scrollTop = list.scrollHeight;
  }, [messages]);

  const connected = status === 'connected';
  useEffect(() => {
    if (connected) setNotSent(false);
  }, [connected]);

  const disabled = !canChat || !connected;
  useEffect(() => {
    if (!focusAfterLogin || disabled) return;
    inputRef.current?.focus();
    setFocusAfterLogin(false);
  }, [focusAfterLogin, disabled]);
  const placeholder = !canChat
    ? '방송 중에만 채팅할 수 있어요'
    : !connected
      ? '채팅에 연결하는 중이에요'
      : guest
        ? '채팅을 입력하세요 (보내려면 로그인)'
        : '채팅을 입력하세요';

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (disabled || !isSendableChat(draft)) return;
    if (guest) {
      requireLogin(() => setFocusAfterLogin(true));
      return;
    }
    if (onSend(draft.trim())) {
      setDraft('');
      setNotSent(false);
      stickRef.current = true;
    } else {
      setNotSent(true);
    }
  };

  return (
    <section aria-labelledby="chat-title" className="flex flex-col overflow-hidden rounded-lg border border-neutral-100 bg-neutral-0 shadow-sm">
      <div className="flex items-center justify-between border-b border-neutral-100 px-5 py-3.5">
        <h2 id="chat-title" className="text-body-lg font-bold">
          실시간 채팅
        </h2>
        <span className="text-caption text-neutral-500">5초에 5개까지</span>
      </div>

      {showNotice && (
        <div role="note" aria-label="판매자 공지" className="flex items-start gap-2.5 border-b border-neutral-100 bg-brand-50 px-5 py-3 text-brand-700">
          <Megaphone className="mt-0.5 size-4 shrink-0" aria-hidden="true" />
          <p className="min-w-0 flex-1 text-label leading-relaxed whitespace-pre-line break-words text-neutral-900">
            <strong className="mr-1.5 font-semibold text-brand-700">공지</strong>
            {notice}
          </p>
          <button
            type="button"
            aria-label="공지 닫기"
            onClick={() => setDismissedNotice(notice)}
            className="-mr-1.5 flex size-7 shrink-0 items-center justify-center rounded-md text-neutral-500 hover:bg-brand-100 hover:text-neutral-900"
          >
            <X className="size-4" aria-hidden="true" />
          </button>
        </div>
      )}

      <ul
        ref={listRef}
        aria-live="polite"
        aria-label="채팅 메시지"
        onScroll={(e) => {
          const el = e.currentTarget;
          stickRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < STICK_TO_BOTTOM_PX;
        }}
        className="flex h-[420px] flex-col gap-2.5 overflow-y-auto px-5 py-3.5"
      >
        {messages.length === 0 ? (
          <li className="m-auto text-center text-body text-neutral-500">
            {canChat ? '아직 채팅이 없어요. 첫 채팅을 남겨 보세요.' : '방송 중에 나눈 채팅이 여기에 나와요.'}
          </li>
        ) : (
          messages.map((message) => (
            <ChatLine
                key={message.messageId}
                message={message}
                isSeller={message.senderId === sellerId}
                isMine={message.senderId === session?.user.userId}
                flagged={flaggedIds?.has(message.messageId) ?? false}
                showHiddenContent={showHiddenContent}
              />
          ))
        )}
      </ul>

      {error && (
        <Notice tone="error" className="mx-3 mb-2">
          {error}
        </Notice>
      )}
      {notSent && (
        <Notice tone="error" className="mx-3 mb-2">
          채팅 연결이 끊겨 보내지 못했어요. 다시 연결되면 보내 주세요.
        </Notice>
      )}

      <form onSubmit={onSubmit} className="flex gap-2 border-t border-neutral-100 p-3">
        <label htmlFor="chat-input" className="sr-only">
          채팅 입력
        </label>
        <div className="relative min-w-0 flex-1">
          <input
            ref={inputRef}
            id="chat-input"
            value={draft}
            onChange={(e) => setDraft(e.target.value)}
            maxLength={CHAT_MAX_LENGTH}
            placeholder={placeholder}
            disabled={disabled}
            autoComplete="off"
            className="h-11 w-full rounded-md border border-neutral-200 bg-neutral-0 pr-16 pl-3.5 text-body placeholder:text-neutral-500 disabled:bg-neutral-50"
          />
          <span aria-hidden="true" className="num pointer-events-none absolute top-0 right-3.5 flex h-11 items-center text-caption text-neutral-500">
            {draft.length}/{CHAT_MAX_LENGTH}
          </span>
        </div>
        <button
          type="submit"
          aria-label="채팅 전송"
          disabled={disabled || !isSendableChat(draft)}
          className="flex size-11 shrink-0 items-center justify-center rounded-md bg-neutral-900 text-neutral-0 transition-colors hover:bg-neutral-700 disabled:cursor-not-allowed disabled:bg-neutral-400"
        >
          <SendHorizontal className="size-[18px]" aria-hidden="true" />
        </button>
      </form>
    </section>
  );
}

/** 가려진 메시지 자리에 시청자가 보는 문구 */
const HIDDEN_MESSAGE_TEXT = '운영 정책에 따라 가려진 메시지예요.';

type ChatLineProps = { message: ChatMessage; isSeller: boolean; isMine: boolean; flagged: boolean; showHiddenContent: boolean };

function ChatLine({ message, isSeller, isMine, flagged, showHiddenContent }: ChatLineProps) {
  // AI 검토로 가려진 채팅·선물 한마디. 판매자는 원래 내용을 줄을 그어 보고, 시청자는 안내 문구만 본다.
  const hiddenContent = message.hidden && (
    <span className="text-neutral-500">
      <Badge variant="neutral" className="mr-1 h-4.5 px-1.5 align-[1px]">
        가려짐
      </Badge>
      {showHiddenContent ? <span className="line-through">{message.content}</span> : HIDDEN_MESSAGE_TEXT}
    </span>
  );
  if (message.type === 'SYSTEM') {
    return <li className="self-center rounded-full bg-neutral-50 px-3 py-1 text-caption text-neutral-500">{message.content}</li>;
  }
  // 도네이션 선물: 채팅에 한 줄로 남긴다. (방송 화면 이펙트는 따로)
  if (message.type === 'DONATION') {
    return (
      <li className="self-center rounded-full bg-brand-50 px-3 py-1 text-caption font-semibold text-brand-700">
        {message.nickname || '알 수 없음'}님이 <span className="num">{formatMileage(message.amount ?? 0)}</span>을 선물했어요
        {message.content && <span className="font-normal text-neutral-700"> · {hiddenContent || message.content}</span>}
      </li>
    );
  }
  // 입찰·낙찰: 서버가 만든 안내 문장을 한 줄로 남긴다. (방송 화면 이펙트는 따로) 10% 이상 올린 입찰은 오른 비율을 붙인다.
  if (message.type === 'BID') {
    const percent = bidIncreasePercent(message.amount ?? 0, message.previousAmount);
    const tier = bidTier(message.amount ?? 0, message.previousAmount);
    return (
      <li className="flex items-start gap-2 rounded-md bg-neutral-50 px-3 py-1.5 text-caption text-neutral-700">
        <HandCoins className="mt-px size-3.5 shrink-0 text-brand-700" aria-hidden="true" />
        <span className="min-w-0 break-words">
          {message.content}
          {percent !== undefined && tier !== 'normal' && (
            <span className={cn('ml-1 font-bold', tier === 'up10' ? 'text-brand-700' : 'text-gold-300')}>
              (<span className="num">{percent}%</span> 올림)
            </span>
          )}
        </span>
      </li>
    );
  }
  if (message.type === 'AUCTION_SOLD') {
    return (
      <li className="flex items-start gap-2 rounded-md border border-gold-300 bg-neutral-50 px-3 py-1.5 text-caption font-semibold text-gold-300">
        <Gavel className="mt-px size-3.5 shrink-0" aria-hidden="true" />
        <span className="min-w-0 break-words">{message.content}</span>
      </li>
    );
  }
  return (
    <li className={cn('leading-relaxed break-words', flagged && '-mx-2 rounded-md bg-brand-50 px-2 py-1')}>
      {flagged && (
        <Badge variant="warning" className="mr-1 h-4.5 px-1.5 align-[1px]">
          확인 필요
        </Badge>
      )}
      {isSeller && (
        <Badge variant="live" className="mr-1 h-4.5 px-1.5 align-[1px]">
          판매자
        </Badge>
      )}
      <strong className={cn('mr-1.5 text-label font-semibold', isSeller ? 'text-brand-700' : isMine ? 'text-info-700' : 'text-neutral-700')}>
        {message.nickname || '알 수 없음'}
      </strong>
      {hiddenContent || <span>{message.content}</span>}
    </li>
  );
}
