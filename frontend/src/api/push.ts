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

/** 이 브라우저의 구독을 서버에서 지운다. (브라우저 구독은 남겨, 다시 켤 때 권한을 묻지 않게) 실패해도 넘어간다. */
export async function releasePushSubscription() {
  try {
    const subscription = await currentPushSubscription();
    if (!subscription) return;
    await call(api.DELETE('/api/v1/push/subscriptions', { params: { query: { endpoint: subscription.endpoint } } }));
  } catch {
    // 로그아웃은 계속한다. 서버에 남은 구독은 다음 사람이 로그인해 구독하면 그 사람 것으로 바뀐다.
  }
}
