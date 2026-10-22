import { api, call } from './client';

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

/** 진행 중인 구독 저장. 로그아웃 정리는 이것이 끝난 뒤에 한다. */
let pendingSave: Promise<unknown> = Promise.resolve();

/**
 * 구독 저장(POST)을 이 함수로 감싸 보낸다. 로그아웃이 저장보다 먼저 끝나면 저장이 구독을 다시 살려,
 * 다음에 로그인한 사람에게 앞 사람의 알림이 갈 수 있기 때문이다.
 */
export function trackPushSave<T>(save: Promise<T>): Promise<T> {
  pendingSave = save.catch(() => undefined);
  return save;
}

/**
 * 이 브라우저의 구독을 서버에서 지운다. (브라우저 구독은 남겨, 다시 켤 때 권한을 묻지 않게)
 * 진행 중인 구독 저장이 있으면 끝난 뒤에 지운다.
 * 서버에서 지우지 못하면 브라우저 구독을 끊는다. 서버에 남은 구독 주소는 더는 이 브라우저로 오지 않으므로,
 * 같은 브라우저를 쓰는 다음 사람에게 앞 사람의 알림이 뜨지 않는다. 실패해도 로그아웃은 계속한다.
 */
export async function releasePushSubscription() {
  await pendingSave;
  let subscription: PushSubscription | null = null;
  try {
    subscription = await currentPushSubscription();
    if (!subscription) return;
    await call(api.DELETE('/api/v1/push/subscriptions', { params: { query: { endpoint: subscription.endpoint } } }));
  } catch {
    await subscription?.unsubscribe().catch(() => undefined);
  }
}
