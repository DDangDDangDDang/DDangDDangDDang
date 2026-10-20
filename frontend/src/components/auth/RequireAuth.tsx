import { Navigate, Outlet, useLocation } from 'react-router';
import { useSession } from '@/api';

/** 로그인해야 볼 수 있는 화면을 감싼다. 로그인하지 않았으면 로그인 화면으로 보내고, 로그인 후 돌아온다. */
export function RequireAuth() {
  const session = useSession();
  const location = useLocation();

  if (!session) {
    const redirect = encodeURIComponent(location.pathname + location.search);
    return <Navigate to={`/login?redirect=${redirect}`} replace />;
  }
  return <Outlet />;
}
