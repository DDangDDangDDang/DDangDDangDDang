import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';

// 서버 요청은 순서만 기록한다.
const calls = vi.hoisted(() => [] as string[]);
vi.mock('./client', () => ({
  api: { DELETE: (path: string) => ({ path, method: 'DELETE' }) },
  call: async (r: { method: string }) => {
    calls.push(r.method);
    return undefined;
  },
}));

const { releasePushSubscription, runPushSave, PushSaveBlocked } = await import('./push');
const { setSession } = await import('./session');

function deferred() {
  let resolve!: () => void;
  const promise = new Promise<void>((r) => (resolve = r));
  return { promise, resolve };
}

describe('push: 로그아웃 정리와 구독 저장의 순서', () => {
  beforeEach(() => {
    calls.length = 0;
    setSession({ accessToken: 't', user: { userId: 1 } });
    const subscription = { endpoint: 'https://fcm.googleapis.com/fcm/send/x', unsubscribe: vi.fn(async () => true) };
    Object.defineProperty(navigator, 'serviceWorker', {
      configurable: true,
      value: { getRegistration: async () => ({ pushManager: { getSubscription: async () => subscription } }) },
    });
  });

  afterEach(() => {
    setSession(null);
  });

  it('이미 시작한 저장이 여러 개면 모두 끝난 뒤에 서버 구독을 지운다', async () => {
    const first = deferred();
    const second = deferred();
    void runPushSave(async () => {
      await first.promise;
      calls.push('SAVE-1');
    });
    void runPushSave(async () => {
      await second.promise;
      calls.push('SAVE-2');
    });

    const release = releasePushSubscription();
    second.resolve();
    await Promise.resolve();
    first.resolve();
    await release;

    expect(calls).toEqual(['SAVE-2', 'SAVE-1', 'DELETE']);
  });

  it('정리를 시작한 뒤에 시작하려는 저장(예: 권한 요청을 기다리던 켜기)은 막고, 다음 로그인 뒤에는 다시 저장할 수 있다', async () => {
    const release = releasePushSubscription();
    const save = vi.fn(async () => calls.push('SAVE'));
    await expect(runPushSave(save)).rejects.toBeInstanceOf(PushSaveBlocked);
    await release;
    expect(save).not.toHaveBeenCalled();
    expect(calls).toEqual(['DELETE']);

    // 로그아웃이 끝나고 다음 회원이 로그인하면 다시 저장할 수 있다.
    setSession(null);
    setSession({ accessToken: 't2', user: { userId: 2 } });
    await runPushSave(save);
    expect(save).toHaveBeenCalledTimes(1);
  });
});
