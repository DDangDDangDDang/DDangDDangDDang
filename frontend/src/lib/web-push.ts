import { useCallback, useEffect, useSyncExternalStore } from 'react';
import { ApiError, api, call, currentPushSubscription, errorMessage } from '@/api';

/*
 * 브라우저 알림(웹 푸시). 켜 두면 탭을 닫아도 브라우저가 켜져 있는 동안 알림함의 새 알림이 브라우저 알림으로 온다.
 * 1. 알림 권한을 받고 서비스 워커(/sw.js)를 등록한다.
 * 2. 서버의 VAPID 공개키로 pushManager.subscribe → 구독 정보(endpoint, 키)를 서버에 저장한다.
 * 회원마다 켰는지를 이 브라우저에 기억해, 다시 접속하면 구독을 서버와 맞춘다. (서버 키가 바뀌었거나 다른 회원이 쓰던 브라우저)
 * 로그아웃하면 서버의 구독을 지운다. (@/api/push)
 */

const SERVICE_WORKER_URL = '/sw.js';
const enabledKey = (userId: number) => `ddang:web-push:${userId}`;

export type WebPushState = 'unsupported' | 'denied' | 'off' | 'on';

export function webPushSupported() {
  return typeof window !== 'undefined' && 'serviceWorker' in navigator && 'PushManager' in window && 'Notification' in window;
}

/** base64url 공개키를 pushManager.subscribe의 applicationServerKey로 */
export function base64UrlToBytes(value: string): Uint8Array<ArrayBuffer> {
  const base64 = value.replace(/-/g, '+').replace(/_/g, '/').padEnd(Math.ceil(value.length / 4) * 4, '=');
  const binary = atob(base64);
  const bytes = new Uint8Array(binary.length);
  for (let i = 0; i < binary.length; i += 1) bytes[i] = binary.charCodeAt(i);
  return bytes;
}

/** 이미 있는 구독이 지금 서버 키로 만든 것인지 */
export function sameKey(current: ArrayBuffer | null | undefined, serverKey: Uint8Array) {
  if (!current) return false;
  const a = new Uint8Array(current);
  return a.length === serverKey.length && a.every((byte, i) => byte === serverKey[i]);
}

function rememberEnabled(userId: number, enabled: boolean) {
  try {
    if (enabled) localStorage.setItem(enabledKey(userId), '1');
    else localStorage.removeItem(enabledKey(userId));
  } catch {
    // 저장소를 못 쓰면 다음 접속 때 다시 켜면 된다.
  }
}

function wasEnabled(userId: number) {
  try {
    return localStorage.getItem(enabledKey(userId)) === '1';
  } catch {
    return false;
  }
}

/** 서버 키로 구독하고(키가 바뀌었으면 새로) 서버에 저장한다. 권한은 이미 받은 상태여야 한다. */
async function subscribeAndSave() {
  const registration = await navigator.serviceWorker.register(SERVICE_WORKER_URL);
  await navigator.serviceWorker.ready;
  const key = await call(api.GET('/api/v1/push/public-key'));
  if (!key?.publicKey) throw new Error('알림 키를 받지 못했어요.');
  const serverKey = base64UrlToBytes(key.publicKey);

  let subscription = await registration.pushManager.getSubscription();
  if (subscription && !sameKey(subscription.options.applicationServerKey, serverKey)) {
    await subscription.unsubscribe();
    subscription = null;
  }
  subscription ??= await registration.pushManager.subscribe({ userVisibleOnly: true, applicationServerKey: serverKey });

  const json = subscription.toJSON();
  if (!json.endpoint || !json.keys?.p256dh || !json.keys.auth) throw new Error('브라우저가 알림 구독 정보를 주지 않았어요.');
  await call(api.POST('/api/v1/push/subscriptions', { body: { endpoint: json.endpoint, p256dh: json.keys.p256dh, auth: json.keys.auth } }));
}

/*
 * 상태는 화면 전체가 함께 쓴다. (헤더 알림함과 경매 상세 [알림 받기] 안내가 같은 상태를 보도록)
 */
type Shared = { userId?: number; state: WebPushState; pending: boolean; error?: string };
let shared: Shared = { state: 'off', pending: false };
const listeners = new Set<() => void>();
/** 켜 둔 회원의 구독을 서버와 맞춘 회원 (접속마다 한 번) */
let syncedUserId: number | undefined;

function update(next: Partial<Shared>) {
  shared = { ...shared, ...next };
  listeners.forEach((listener) => listener());
}

function subscribeStore(listener: () => void) {
  listeners.add(listener);
  return () => {
    listeners.delete(listener);
  };
}

/**
 * 이 회원의 브라우저 알림 상태와 켜기·끄기.
 * 켜 둔 회원이 다시 접속하면 구독을 서버와 다시 맞춘다. (서버를 다시 켜 키가 바뀐 로컬 개발, 같은 브라우저를 쓰던 다른 회원)
 */
export function useWebPush(userId: number | undefined) {
  const snapshot = useSyncExternalStore(subscribeStore, () => shared);
  const state = snapshot.userId === userId ? snapshot.state : initialState(userId);

  useEffect(() => {
    if (userId === undefined || syncedUserId === userId) return;
    syncedUserId = userId;
    update({ userId, state: initialState(userId), pending: false, error: undefined });
    if (!webPushSupported() || Notification.permission !== 'granted' || !wasEnabled(userId)) return;
    subscribeAndSave()
      .then(() => shared.userId === userId && update({ state: 'on' }))
      .catch(() => shared.userId === userId && update({ state: 'off' }));
  }, [userId]);

  const enable = useCallback(async () => {
    if (userId === undefined || !webPushSupported()) return;
    update({ userId, pending: true, error: undefined });
    try {
      const permission = await Notification.requestPermission();
      if (permission !== 'granted') {
        update({ state: permission === 'denied' ? 'denied' : 'off' });
        return;
      }
      await subscribeAndSave();
      rememberEnabled(userId, true);
      update({ state: 'on' });
    } catch (e) {
      update({ error: e instanceof ApiError ? errorMessage(e) : '이 브라우저에서 알림을 켜지 못했어요. 브라우저 설정에서 알림을 허용했는지 확인해 주세요.' });
    } finally {
      update({ pending: false });
    }
  }, [userId]);

  const disable = useCallback(async () => {
    if (userId === undefined) return;
    update({ userId, pending: true, error: undefined });
    try {
      const subscription = await currentPushSubscription();
      if (subscription) {
        await call(api.DELETE('/api/v1/push/subscriptions', { params: { query: { endpoint: subscription.endpoint } } }));
        await subscription.unsubscribe();
      }
      rememberEnabled(userId, false);
      update({ state: 'off' });
    } catch (e) {
      update({ error: e instanceof ApiError ? errorMessage(e) : '이 브라우저에서 알림을 끄지 못했어요.' });
    } finally {
      update({ pending: false });
    }
  }, [userId]);

  const mine = snapshot.userId === userId;
  return { state, pending: mine && snapshot.pending, error: mine ? snapshot.error : undefined, enable, disable };
}

function initialState(userId: number | undefined): WebPushState {
  if (!webPushSupported()) return 'unsupported';
  if (Notification.permission === 'denied') return 'denied';
  return userId !== undefined && Notification.permission === 'granted' && wasEnabled(userId) ? 'on' : 'off';
}
