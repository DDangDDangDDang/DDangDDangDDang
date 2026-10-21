// 화면에서는 이 파일에서만 가져다 쓴다.
export { api, call } from './client';
export { ApiError, errorMessage, isErrorCode } from './errors';
export { login, logout, restoreSession, replaceSession, clearSession } from './auth';
export { useSession, getSession, subscribeSession, type Session, type UserInfo } from './session';
export type { components, paths, operations } from './generated/schema';
export type { ErrorCode } from './generated/error-codes';

import type { components } from './generated/schema';
/** 명세의 스키마 타입을 이름으로 꺼낸다. 예: Schema<'AuctionResponse'> */
export type Schema<Name extends keyof components['schemas']> = components['schemas'][Name];
export {
  connectRealtime,
  REALTIME,
  type ChatMessage,
  type LiveStreamUpdatedMessage,
  type RealtimeConnection,
  type RealtimeError,
  type RealtimeStatus,
} from './realtime';
