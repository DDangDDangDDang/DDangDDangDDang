import { SendHorizontal } from 'lucide-react';
import { useEffect, useLayoutEffect, useRef, useState, type FormEvent } from 'react';
import { useSession, type ChatMessage, type RealtimeStatus } from '@/api';
import { Badge, Notice } from '@/components/ui';
import { cn } from '@/lib/cn';
import { CHAT_MAX_LENGTH, isSendableChat } from './live-room';

type Props = {
  messages: ChatMessage[];
  sellerId?: number;
  /** 방송 중일 때만 보낼 수 있다. */
  canChat: boolean;
  status: RealtimeStatus;
  /** 채팅 전송 실패 안내 (/user/queue/errors) */
  error?: string;
  onSend: (content: string) => boolean;
};

/** 스크롤이 맨 아래에서 이만큼(px) 안에 있으면 새 메시지가 올 때 따라 내려간다. */
const STICK_TO_BOTTOM_PX = 48;

/** 실시간 채팅: 메시지 목록 + 입력. 메시지는 접속한 뒤부터 쌓인다. (이전 채팅 API 없음) */
export function LiveChat({ messages, sellerId, canChat, status, error, onSend }: Props) {
  const session = useSession();
  const [draft, setDraft] = useState('');
  const [notSent, setNotSent] = useState(false);
  const listRef = useRef<HTMLUListElement>(null);
  const stickRef = useRef(true);

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
  const placeholder = !canChat ? '방송 중에만 채팅할 수 있어요' : !connected ? '채팅에 연결하는 중이에요' : '채팅을 입력하세요';

  const onSubmit = (e: FormEvent) => {
    e.preventDefault();
    if (disabled || !isSendableChat(draft)) return;
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
            <ChatLine key={message.messageId} message={message} isSeller={message.senderId === sellerId} isMine={message.senderId === session?.user.userId} />
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

function ChatLine({ message, isSeller, isMine }: { message: ChatMessage; isSeller: boolean; isMine: boolean }) {
  if (message.type === 'SYSTEM') {
    return <li className="self-center rounded-full bg-neutral-50 px-3 py-1 text-caption text-neutral-500">{message.content}</li>;
  }
  return (
    <li className="leading-relaxed break-words">
      {isSeller && (
        <Badge variant="live" className="mr-1 h-4.5 px-1.5 align-[1px]">
          판매자
        </Badge>
      )}
      <strong className={cn('mr-1.5 text-label font-semibold', isSeller ? 'text-brand-700' : isMine ? 'text-info-700' : 'text-neutral-700')}>
        {message.nickname || '알 수 없음'}
      </strong>
      <span>{message.content}</span>
    </li>
  );
}
