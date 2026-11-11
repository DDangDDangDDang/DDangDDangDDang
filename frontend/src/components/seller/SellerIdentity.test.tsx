import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import { SellerIdentity } from './SellerIdentity';

const state = vi.hoisted(() => ({
  profile: {} as { nickname?: string; verifiedSeller?: boolean; botSeller?: boolean },
  openProfile: vi.fn(),
}));

vi.mock('@/lib/use-seller', () => ({ useSellerProfile: () => ({ data: state.profile }) }));
vi.mock('./seller-profile-context', () => ({ useOpenSellerProfile: () => state.openProfile }));

describe('판매자 이름', () => {
  beforeEach(() => {
    state.openProfile.mockClear();
  });

  it('라이브 봇은 인증 전이어도 누르면 프로필 창이 열린다', async () => {
    state.profile = { nickname: '봇상점', verifiedSeller: false, botSeller: true };
    render(<SellerIdentity sellerId={7} />);

    await userEvent.click(screen.getByRole('button', { name: '봇상점 판매자 프로필 보기' }));

    expect(state.openProfile).toHaveBeenCalledWith(7);
  });

  it('인증 전인 일반 회원은 누를 수 없다', () => {
    state.profile = { nickname: '일반회원', verifiedSeller: false, botSeller: false };
    render(<SellerIdentity sellerId={8} />);

    expect(screen.queryByRole('button')).toBeNull();
  });
});
