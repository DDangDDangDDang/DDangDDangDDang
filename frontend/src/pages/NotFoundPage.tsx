import { Link } from 'react-router';
import { EmptyState, buttonVariants } from '@/components/ui';

export function NotFoundPage() {
  return (
    <EmptyState
      title="페이지를 찾을 수 없어요"
      description="주소가 바뀌었거나 삭제된 페이지예요."
      action={
        <Link to="/" className={buttonVariants({ variant: 'outline' })}>
          홈으로
        </Link>
      }
    />
  );
}
