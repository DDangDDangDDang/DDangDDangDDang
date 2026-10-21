import type { ChatMessage } from '@/api';

/*
 * 방송 시청 화면의 계산 로직. (화면과 분리해 단위 테스트한다)
 */

// LiveKit 참가자 identity 앞부분. 백엔드 LiveStreamService의 publisherIdentity / VIEWER_IDENTITY_PREFIX와 같다.
export const SELLER_IDENTITY_PREFIX = 'seller-';
export const VIEWER_IDENTITY_PREFIX = 'viewer-';

type Participant = { identity: string; name?: string };

/**
 * LiveKit 방의 다른 참가자 목록에서 판매자 이름과 시청자 수(나 포함)를 구한다.
 * 판매자는 시청자 수에 넣지 않는다.
 */
export function summarizeParticipants(remote: Iterable<Participant>): { sellerName?: string; viewerCount: number } {
  let sellerName: string | undefined;
  let viewerCount = 1; // 나
  for (const participant of remote) {
    if (participant.identity.startsWith(SELLER_IDENTITY_PREFIX)) {
      sellerName = participant.name || undefined;
    } else if (participant.identity.startsWith(VIEWER_IDENTITY_PREFIX)) {
      viewerCount += 1;
    }
  }
  return { sellerName, viewerCount };
}

/** 화면에 남겨 둘 채팅 수. 오래된 메시지부터 지운다. */
export const MAX_CHAT_MESSAGES = 200;

/** 채팅 목록 끝에 새 메시지를 붙인다. 같은 메시지가 두 번 오면(재연결 등) 한 번만 남긴다. */
export function appendChatMessage(list: ChatMessage[], message: ChatMessage, max = MAX_CHAT_MESSAGES): ChatMessage[] {
  if (list.some((m) => m.messageId === message.messageId)) return list;
  const next = [...list, message];
  return next.length > max ? next.slice(next.length - max) : next;
}

/** 채팅 입력 최대 글자 수 (docs/api/README.md: 1~200자) */
export const CHAT_MAX_LENGTH = 200;

/** 보낼 수 있는 채팅인지: 앞뒤 공백을 뺀 내용이 1~200자 */
export function isSendableChat(content: string): boolean {
  const trimmed = content.trim();
  return trimmed.length > 0 && trimmed.length <= CHAT_MAX_LENGTH;
}
