import { useCallback, useEffect } from 'react';
import { useLocation, useNavigate } from 'react-router';

/*
 * 방송을 보는 중에는 시청 화면을 벗어나는 이동을 새 탭에서 연다. (방송 시청이 끊기지 않게)
 * - 화면의 링크(헤더 메뉴, 알림 팝업, 경매 목록 등): WatchNavigationGuard가 누르기를 가로채 새 탭으로 연다.
 * - 코드로 옮기는 이동(알림함 항목 등): useLeaveSafeNavigate를 쓴다.
 * - 브라우저 알림을 누를 때: public/sw.js가 시청 중인 탭은 건드리지 않는다.
 * 로그인·회원가입은 이 탭에서 옮긴다. (새 탭에서 로그인하면 시청 탭은 로그인 전 상태로 남고, 로그인 뒤 이 방송으로 돌아온다)
 */

const WATCH_PATH = /^\/lives\/\d+\/?$/;
const SAME_TAB_PATHS = ['/login', '/signup'];
/** 끝 슬래시만 다른 같은 화면(/lives/12/ 와 /lives/12)을 같게 본다. */
const normalize = (path: string) => (path.length > 1 ? path.replace(/\/+$/, '') : path);

export function isWatchPath(pathname: string) {
  return WATCH_PATH.test(pathname);
}

/** 시청 화면(from)에서 이 주소(to)로 갈 때 새 탭으로 열어야 하는지 */
export function shouldOpenInNewTab(from: string, to: URL, origin: string) {
  if (!isWatchPath(from) || to.origin !== origin) return false;
  if (normalize(to.pathname) === normalize(from) || SAME_TAB_PATHS.includes(to.pathname)) return false;
  return true;
}

function openInNewTab(href: string) {
  window.open(href, '_blank', 'noopener');
}

/** 앱 전체에 하나. 시청 화면에서 다른 화면으로 가는 링크를 누르면 새 탭으로 연다. */
export function WatchNavigationGuard() {
  const { pathname } = useLocation();

  useEffect(() => {
    if (!isWatchPath(pathname)) return;
    // 캡처 단계에서 먼저 받아, 라우터 링크가 이 탭에서 옮기기 전에 막는다. (라우터는 defaultPrevented면 옮기지 않음)
    const onClick = (event: MouseEvent) => {
      if (event.defaultPrevented || event.button !== 0 || event.metaKey || event.ctrlKey || event.shiftKey || event.altKey) return;
      const anchor = (event.target as Element | null)?.closest?.('a[href]');
      if (!(anchor instanceof HTMLAnchorElement) || anchor.hasAttribute('download')) return;
      if (anchor.target && anchor.target !== '_self') return;
      const to = new URL(anchor.href, window.location.href);
      if (!shouldOpenInNewTab(window.location.pathname, to, window.location.origin)) return;
      event.preventDefault();
      openInNewTab(to.href);
    };
    document.addEventListener('click', onClick, true);
    return () => document.removeEventListener('click', onClick, true);
  }, [pathname]);

  return null;
}

/** navigate 대신 쓴다. 시청 화면이면 새 탭으로 연다. */
export function useLeaveSafeNavigate() {
  const navigate = useNavigate();
  const { pathname } = useLocation();
  return useCallback(
    (path: string) => {
      const to = new URL(path, window.location.href);
      if (shouldOpenInNewTab(pathname, to, window.location.origin)) openInNewTab(to.href);
      else navigate(path);
    },
    [navigate, pathname],
  );
}
