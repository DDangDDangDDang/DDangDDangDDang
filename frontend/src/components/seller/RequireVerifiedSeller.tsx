import { Link, Outlet } from 'react-router';
import { errorMessage, useSession } from '@/api';
import { PageHeader } from '@/components/layout/PageHeader';
import { buttonVariants, Card, EmptyState, ErrorState, Skeleton } from '@/components/ui';
import { useSellerProfile } from '@/lib/use-seller';

/**
 * 판매자 인증을 받은 회원만 여는 화면 (내 상품, 상품 등록·수정, 경매 등록).
 * 인증 전이면 화면 대신 인증 안내를 보여 준다. 상단 [내 상품] 탭도 인증 판매자에게만 보인다. (AppLayout)
 * router.tsx에서 RequireAuth 아래, 해당 화면들을 감싸는 데 쓴다.
 */
export function RequireVerifiedSeller() {
  const session = useSession();
  const profile = useSellerProfile(session?.user.userId);

  if (profile.isPending) {
    return (
      <div className="flex flex-col gap-3" aria-busy="true">
        <Skeleton className="h-8 w-48" />
        <Skeleton className="h-40 rounded-lg" />
      </div>
    );
  }
  if (profile.isError) return <ErrorState message={errorMessage(profile.error)} onRetry={() => void profile.refetch()} />;
  if (profile.data?.verifiedSeller) return <Outlet />;

  return (
    <div className="mx-auto flex max-w-[720px] flex-col gap-4">
      <PageHeader title="판매자 전용 화면" />
      <Card>
        <EmptyState
          title="판매자 인증을 받아야 상품을 등록하고 팔 수 있어요"
          description="[판매자 인증]에서 상점 이름과 소개를 적어 신청하면 관리자가 확인해요. 승인되면 내 상품과 판매 내역을 볼 수 있어요."
          action={
            <Link to="/me/seller" className={buttonVariants({ variant: 'primary' })}>
              판매자 인증 신청
            </Link>
          }
        />
      </Card>
    </div>
  );
}
