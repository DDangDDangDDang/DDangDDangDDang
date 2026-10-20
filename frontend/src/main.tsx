import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router';
import { ApiError, getSession, restoreSession, subscribeSession } from '@/api';
import { router } from './router';
import './styles/index.css';

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      // 4xx(권한 없음, 없는 리소스 등)는 다시 시도해도 같으므로 재시도하지 않는다.
      retry: (failureCount, error) =>
        failureCount < 2 && !(error instanceof ApiError && error.status >= 400 && error.status < 500),
      refetchOnWindowFocus: false,
    },
  },
});

// 로그아웃하거나 다른 계정으로 바뀌면 이전 계정의 데이터(내 정보, 포인트, 주문 등)를 캐시에서 지운다.
// 같은 계정의 토큰만 바뀐 경우(재발급, 닉네임 변경)는 지우지 않는다.
let currentUserId = getSession()?.user.userId;
subscribeSession(() => {
  const nextUserId = getSession()?.user.userId;
  if (nextUserId !== currentUserId) {
    queryClient.clear();
    currentUserId = nextUserId;
  }
});

// 새로고침해도 로그인이 유지되도록, 첫 화면을 그리기 전에 리프레시 토큰 쿠키로 세션을 되살린다.
restoreSession().finally(() => {
  createRoot(document.getElementById('root')!).render(
    <StrictMode>
      <QueryClientProvider client={queryClient}>
        <RouterProvider router={router} />
      </QueryClientProvider>
    </StrictMode>,
  );
});
