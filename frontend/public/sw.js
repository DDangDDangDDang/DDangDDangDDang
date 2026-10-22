/*
 * 브라우저 알림(웹 푸시) 서비스 워커. 탭을 닫아도 브라우저가 켜져 있으면 서버가 보낸 알림을 띄운다.
 * 서버(WebPushSender)가 보내는 내용: { notificationId, type, title, body, link, tag }
 * 이 파일은 빌드하지 않고 그대로 /sw.js로 나간다. (src/lib/web-push.ts에서 등록)
 */

self.addEventListener('install', () => {
  self.skipWaiting();
});

self.addEventListener('activate', (event) => {
  // 이미 열려 있는 탭도 바로 이 서비스 워커가 맡게 한다. (알림을 눌렀을 때 그 탭에서 화면을 옮기기 위해)
  event.waitUntil(self.clients.claim());
});

self.addEventListener('push', (event) => {
  let data = {};
  try {
    data = event.data ? event.data.json() : {};
  } catch {
    data = { body: event.data ? event.data.text() : '' };
  }
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then((windows) => {
      // 사이트 탭을 보고 있으면 화면 안 알림(팝업·알림함)으로 충분하므로 브라우저 알림은 띄우지 않는다.
      if (windows.some((client) => client.focused && client.visibilityState === 'visible')) return undefined;
      return self.registration.showNotification(data.title || '땅땅땅 알림', {
        body: data.body || '',
        tag: data.tag,
        data: { link: data.link || '/' },
      });
    }),
  );
});

self.addEventListener('notificationclick', (event) => {
  event.notification.close();
  const url = new URL(event.notification.data?.link || '/', self.location.origin).href;
  event.waitUntil(
    self.clients.matchAll({ type: 'window', includeUncontrolled: true }).then(async (windows) => {
      const tab = windows.find((client) => client.url.startsWith(self.location.origin));
      if (tab) {
        await tab.focus();
        // 열려 있는 탭에서 그 화면으로 옮긴다. (탭을 맡지 못했으면 새 창으로)
        if ('navigate' in tab) {
          try {
            await tab.navigate(url);
            return;
          } catch {
            // 아래에서 새 창으로 연다
          }
        }
      }
      await self.clients.openWindow(url);
    }),
  );
});
