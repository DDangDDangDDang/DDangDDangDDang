import { QueryClient, QueryClientProvider } from '@tanstack/react-query';
import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { RouterProvider } from 'react-router';
import { ApiError, restoreSession } from '@/api';
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
