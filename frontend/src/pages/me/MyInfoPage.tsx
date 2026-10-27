import { MyInfoContent } from '@/components/account/MyInfoContent';
import { PageHeader } from '@/components/layout/PageHeader';

/** 내 정보 화면 (/me). 헤더의 내 닉네임을 누르면 같은 내용이 창으로도 뜬다. (MyInfoDialog) */
export function MyInfoPage() {
  return (
    <div className="mx-auto max-w-[720px]">
      <PageHeader title="내 정보" />
      <MyInfoContent />
    </div>
  );
}
