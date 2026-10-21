import type { Schema } from '@/api';

/*
 * 1:1 채팅 계산 로직. (화면과 분리해 단위 테스트한다)
 */

type Message = Schema<'DmMessageResponse'>;

/** 메시지 최대 글자 수 (DmMessageSendRequest) */
export const DM_MAX_LENGTH = 1000;
/** 신고 상세 최대 글자 수 (DmReportRequest) */
export const DM_REPORT_DETAIL_MAX_LENGTH = 500;

/**
 * 서버가 최신순 페이지로 주는 메시지를 오래된 것부터 한 줄로 편다. 같은 메시지(실시간으로 먼저 받은 것 등)는 한 번만 남긴다.
 */
export function flattenMessages(pages: (Message[] | undefined)[]): Message[] {
  const byId = new Map<number, Message>();
  for (const page of pages) {
    for (const message of page ?? []) {
      if (message.messageId !== undefined) byId.set(message.messageId, message);
    }
  }
  return [...byId.values()].sort((a, b) => (a.messageId ?? 0) - (b.messageId ?? 0));
}

/** 최신순 첫 페이지 맨 앞에 새 메시지를 넣는다. 이미 있으면 그대로 둔다. (실시간·보내기 응답) */
export function prependMessage(page: Message[], message: Message): Message[] {
  if (page.some((m) => m.messageId === message.messageId)) return page;
  return [message, ...page];
}

/** 내가 보낸 메시지를 상대가 아직 읽지 않았는지 ("1" 표시) */
export function isUnreadByOther(message: Message, myId: number | undefined, otherLastReadMessageId: number | undefined): boolean {
  if (message.senderId !== myId || message.messageId === undefined) return false;
  return message.messageId > (otherLastReadMessageId ?? 0);
}

/** 가장 마지막(큰) 메시지 ID */
export function lastMessageId(messages: Message[]): number | undefined {
  return messages.reduce<number | undefined>((max, m) => (m.messageId !== undefined && (max === undefined || m.messageId > max) ? m.messageId : max), undefined);
}

/** 날짜가 바뀌는 메시지인지 (앞 메시지와 날짜가 다르면 날짜 구분선을 넣는다) */
export function startsNewDay(previous: Message | undefined, current: Message): boolean {
  if (!current.sentAt) return false;
  if (!previous?.sentAt) return true;
  return previous.sentAt.slice(0, 10) !== current.sentAt.slice(0, 10);
}

/** 보낼 수 있는 내용인지: 앞뒤 공백을 뺀 내용이 1~1000자 */
export function isSendableDm(content: string): boolean {
  const trimmed = content.trim();
  return trimmed.length > 0 && trimmed.length <= DM_MAX_LENGTH;
}

/** 응답을 못 받고 다시 보낼 때 서버가 같은 메시지로 알아보도록 보내기마다 만드는 ID */
export function newClientMessageId(): string {
  return typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(36).slice(2)}`;
}
