import { api, call } from './client';
import { getSession, subscribeSession } from './session';

/*
 * 브라우저 알림(웹 푸시) 구독의 서버 쪽 처리. 권한 요청·구독 만들기는 @/lib/web-push가 맡는다.
 * 로그아웃할 때 이 브라우저의 구독을 서버에서 지워, 다음에 로그인한 사람에게 앞 사람의 알림이 가지 않게 한다.
 */

/** 이 브라우저의 지금 구독 (서비스 워커가 없거나 구독하지 않았으면 null) */
export async function currentPushSubscription(): Promise<PushSubscription | null> {
  if (typeof navigator === 'undefined' || !('serviceWorker' in navigator)) return null;
  const registration = await navigator.serviceWorker.getRegistration();
  return (await registration?.pushManager?.getSubscription()) ?? null;
}

/** 로그아웃 정리를 시작한 뒤에 새 구독 저장을 시작하려 할 때 */
export class PushSaveBlocked extends Error {
  constructor() {
    super('로그아웃 중이라 브라우저 알림 구독을 저장하지 않아요.');
  }
}

/** 진행 중인 구독 저장들. 로그아웃 정리는 모두 끝난 뒤에 한다. */
const pendingSaves = new Set<Promise<unknown>>();
/** 로그아웃 정리를 시작했으면, 세션이 바뀔 때(로그아웃 완료·다음 로그인)까지 새 저장을 막는다. */
let releasing = false;
let releasingSession = getSession();
subscribeSession(() => {
  if (getSession() !== releasingSession) releasing = false;
});

/**
 * 구독 만들기·저장을 이 함수로 감싸 시작한다. 로그아웃 정리가 시작됐으면 시작하지 않고 PushSaveBlocked로 끝난다.
 * (로그아웃 뒤에 저장이 끝나면 구독이 다시 살아나, 다음에 로그인한 사람에게 앞 사람의 알림이 갈 수 있다)
 */
export function runPushSave<T>(save: () => Promise<T>): Promise<T> {
  if (releasing) return Promise.reject(new PushSaveBlocked());
  const running = save();
  const tracked = running.catch(() => undefined);
  pendingSaves.add(tracked);
  void tracked.then(() => pendingSaves.delete(tracked));
  return running;
}

/**
 * 이 브라우저의 구독을 서버에서 지운다. (브라우저 구독은 남겨, 다시 켤 때 권한을 묻지 않게)
 * 먼저 새 저장을 막고, 이미 시작한 저장이 모두 끝난 뒤에 지운다.
 * 서버에서 지우지 못하면 브라우저 구독을 끊는다. 서버에 남은 구독 주소는 더는 이 브라우저로 오지 않으므로,
 * 같은 브라우저를 쓰는 다음 사람에게 앞 사람의 알림이 뜨지 않는다. 실패해도 로그아웃은 계속한다.
 */
export async function releasePushSubscription() {
  releasing = true;
  releasingSession = getSession();
  await Promise.all([...pendingSaves]);
  let subscription: PushSubscription | null = null;
  try {
    subscription = await currentPushSubscription();
    if (!subscription) return;
    await call(api.DELETE('/api/v1/push/subscriptions', { params: { query: { endpoint: subscription.endpoint } } }));
  } catch {
    await subscription?.unsubscribe().catch(() => undefined);
  }
}
