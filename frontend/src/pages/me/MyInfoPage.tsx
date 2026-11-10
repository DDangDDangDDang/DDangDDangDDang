import { MyInfoContent } from '@/components/account/MyInfoContent';
import { MySanctionsNotice } from '@/components/account/MySanctionsNotice';
import { PageHeader } from '@/components/layout/PageHeader';

/** 내 정보 화면 (/me). 헤더의 내 닉네임을 누르면 같은 내용이 창으로도 뜬다. (MyInfoDialog) */
export function MyInfoPage() {
  return (
    <div className="mx-auto max-w-[720px]">
      <PageHeader title="내 정보" />
      {/* 본인 책임 취소가 반복돼 입찰·경매 등록이 정지 중일 때만 보인다 */}
      <MySanctionsNotice className="mb-4" />
      <MyInfoContent />
    </div>
  );
}
