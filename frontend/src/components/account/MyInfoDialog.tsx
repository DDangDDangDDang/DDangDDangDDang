import { Dialog } from '@/components/ui';
import { MyInfoContent } from './MyInfoContent';

/** 헤더의 내 닉네임을 누르면 뜨는 내 정보 창. 내용은 /me 화면과 같다. 다른 화면으로 가는 버튼을 누르면 창을 닫는다. */
export function MyInfoDialog({ open, onClose }: { open: boolean; onClose: () => void }) {
  return (
    <Dialog open={open} title="내 정보" onClose={onClose} className="w-[640px]">
      {/* 창을 열 때마다 새로 그려 닉네임 변경 입력 등이 남지 않게 한다. */}
      {open && <MyInfoContent onNavigate={onClose} />}
    </Dialog>
  );
}
