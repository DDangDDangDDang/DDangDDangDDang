import { createBrowserRouter } from 'react-router';
import { RequireAuth } from '@/components/auth/RequireAuth';
import { AppLayout } from '@/components/layout/AppLayout';
import { RequireVerifiedSeller } from '@/components/seller/RequireVerifiedSeller';
import { FindEmailPage } from '@/pages/auth/FindEmailPage';
import { LoginPage } from '@/pages/auth/LoginPage';
import { PasswordResetPage } from '@/pages/auth/PasswordResetPage';
import { SignupPage } from '@/pages/auth/SignupPage';
import { CategoryAdminPage } from '@/pages/admin/CategoryAdminPage';
import { DmReportsPage } from '@/pages/admin/DmReportsPage';
import { SellerApplicationsPage } from '@/pages/admin/SellerApplicationsPage';
import { AuctionDetailPage } from '@/pages/auctions/AuctionDetailPage';
import { AuctionListPage } from '@/pages/auctions/AuctionListPage';
import { DevComponentsPage } from '@/pages/DevComponentsPage';
import { HomePage } from '@/pages/HomePage';
import { LiveListPage } from '@/pages/live/LiveListPage';
import { LiveWatchPage } from '@/pages/live/LiveWatchPage';
import { MyInfoPage } from '@/pages/me/MyInfoPage';
import { MessagesPage } from '@/pages/messages/MessagesPage';
import { PointsPage } from '@/pages/me/PointsPage';
import { SellerApplicationPage } from '@/pages/me/SellerApplicationPage';
import { NotFoundPage } from '@/pages/NotFoundPage';
import { OrderDetailPage } from '@/pages/orders/OrderDetailPage';
import { OrdersPage } from '@/pages/orders/OrdersPage';
import { MyProductsPage } from '@/pages/products/MyProductsPage';
import { ProductAuctionPage } from '@/pages/products/ProductAuctionPage';
import { ProductCreatePage } from '@/pages/products/ProductCreatePage';
import { ProductEditPage } from '@/pages/products/ProductEditPage';
import { SellerStudioPage } from '@/pages/studio/SellerStudioPage';

/**
 * 화면 목록. 새 화면은 src/pages에 만들고 여기에 경로를 추가한다.
 * 로그인해야 하는 화면은 RequireAuth 아래에 둔다.
 * 화면 구성은 디자인 캔버스(와이어프레임, 고해상도 시안)를 따른다. (frontend/CLAUDE.md)
 */
export const router = createBrowserRouter([
  {
    element: <AppLayout />,
    children: [
      { path: '/', element: <HomePage /> },

      // 로그인 전
      { path: '/login', element: <LoginPage /> },
      { path: '/signup', element: <SignupPage /> },
      { path: '/find-email', element: <FindEmailPage /> },
      // 로그인 전 재설정 + 내 정보의 [비밀번호 변경]이 같은 화면을 쓴다.
      { path: '/password-reset', element: <PasswordResetPage /> },

      // 로그인 없이 둘러보기 (방송 시청·경매 정보). 채팅·입찰·문의는 화면 안에서 로그인 창을 띄운다. (LoginRequiredDialog)
      { path: '/lives', element: <LiveListPage /> },
      { path: '/lives/:liveStreamId', element: <LiveWatchPage /> },
      { path: '/auctions', element: <AuctionListPage /> },
      { path: '/auctions/:auctionId', element: <AuctionDetailPage /> },

      // 로그인 후
      {
        element: <RequireAuth />,
        children: [
          { path: '/me', element: <MyInfoPage /> },
          { path: '/points', element: <PointsPage /> },
          { path: '/me/seller', element: <SellerApplicationPage /> },
          { path: '/orders', element: <OrdersPage /> },
          { path: '/orders/:orderId', element: <OrderDetailPage /> },
          { path: '/studio', element: <SellerStudioPage /> },
          { path: '/messages', element: <MessagesPage /> },
          { path: '/messages/:userId', element: <MessagesPage /> },
          // 판매자 인증을 받은 회원만 (인증 전이면 인증 안내)
          {
            element: <RequireVerifiedSeller />,
            children: [
              { path: '/me/products', element: <MyProductsPage /> },
              { path: '/products/new', element: <ProductCreatePage /> },
              { path: '/products/:productId/edit', element: <ProductEditPage /> },
              { path: '/products/:productId/auction', element: <ProductAuctionPage /> },
            ],
          },
          // 관리자 (역할은 화면 안에서 확인하고, 서버도 ADMIN만 허용한다)
          { path: '/admin/categories', element: <CategoryAdminPage /> },
          { path: '/admin/dm-reports', element: <DmReportsPage /> },
          { path: '/admin/seller-applications', element: <SellerApplicationsPage /> },
        ],
      },

      // 공통 컴포넌트 모음. 개발 중 확인용이라 메뉴에는 노출하지 않는다.
      { path: '/dev/components', element: <DevComponentsPage /> },
      { path: '*', element: <NotFoundPage /> },
    ],
  },
]);
