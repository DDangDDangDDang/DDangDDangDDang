import { createBrowserRouter } from 'react-router';
import { AppLayout } from '@/components/layout/AppLayout';
import { DevComponentsPage } from '@/pages/DevComponentsPage';
import { HomePage } from '@/pages/HomePage';
import { NotFoundPage } from '@/pages/NotFoundPage';

/**
 * 화면 목록. 새 화면은 src/pages에 만들고 여기에 경로를 추가한다.
 * 화면 구성은 디자인 캔버스(와이어프레임, 고해상도 시안)를 따른다. (frontend/CLAUDE.md)
 */
export const router = createBrowserRouter([
  {
    element: <AppLayout />,
    children: [
      { path: '/', element: <HomePage /> },
      // 공통 컴포넌트 모음. 개발 중 확인용이라 빌드에는 남아도 메뉴에는 노출하지 않는다.
      { path: '/dev/components', element: <DevComponentsPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
