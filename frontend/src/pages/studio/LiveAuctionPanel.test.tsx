import { render, screen, within } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { MemoryRouter } from 'react-router';
import { beforeEach, describe, expect, it, vi } from 'vitest';
import type { Schema } from '@/api';
import { LiveAuctionStatus } from './LiveAuctionPanel';
import type { LiveAuctions } from './use-live-auctions';

const auction = (id: number, productName: string): Schema<'AuctionResponse'> => ({
  id,
  productName,
  status: 'ONGOING',
  startAt: '2026-10-24T11:00:00',
  endAt: '2099-10-24T13:00:00',
  currentPrice: 10_000,
  bidCount: 1,
});

function setup(featuredAuctionIds: number[]) {
  const mutate = vi.fn();
  const auctions = {
    liveStreamId: 1,
    featuredAuctionIds,
    auctions: [auction(1, '라이카 M6'), auction(2, '롤렉스 시계'), auction(3, '나이키 운동화')],
    loadingAuctions: false,
    feature: { mutate, isPending: false, isError: false, variables: undefined, error: null, reset: vi.fn() },
    cancel: { mutate: vi.fn(), isPending: false, isError: false, error: null, reset: vi.fn() },
  } as unknown as LiveAuctions;
  render(
    <MemoryRouter>
      <LiveAuctionStatus auctions={auctions} />
    </MemoryRouter>,
  );
  return { mutate };
}

describe('LiveAuctionStatus 영상에 띄우기', () => {
  beforeEach(() => {
    // jsdom에는 <dialog>의 showModal·close가 없다.
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
    };
  });

  it('하나만 떠 있으면 다른 경매도 바로 띄운다', async () => {
    const { mutate } = setup([1]);

    await userEvent.click(screen.getByRole('button', { name: '롤렉스 시계 영상에 띄우기' }));

    expect(mutate).toHaveBeenCalledWith({ auctionId: 2, on: true });
  });

  it('이미 2개가 떠 있으면 다른 경매의 띄우기 버튼이 꺼져 있고, 누르면 하나를 내리라는 창을 연다', async () => {
    const { mutate } = setup([1, 2]);

    const blocked = screen.getByRole('button', { name: '나이키 운동화 영상에 띄우기' });
    expect(blocked).toHaveAttribute('aria-disabled', 'true');
    await userEvent.click(blocked);

    expect(mutate).not.toHaveBeenCalled();
    const dialog = screen.getByRole('dialog', { name: '영상 위에는 경매를 2개까지 띄울 수 있어요' });
    expect(dialog).toHaveTextContent('하나를 내린 뒤 다시 띄워 주세요');

    // 창에서 바로 띄운 경매를 내릴 수 있다.
    await userEvent.click(within(dialog).getByRole('button', { name: '라이카 M6 영상에서 내리기' }));
    expect(mutate).toHaveBeenCalledWith({ auctionId: 1, on: false }, expect.anything());
  });

  it('떠 있는 경매의 내리기 버튼은 2개가 떠 있어도 누를 수 있다', async () => {
    const { mutate } = setup([1, 2]);

    await userEvent.click(screen.getAllByRole('button', { name: '롤렉스 시계 영상에서 내리기' })[0] as HTMLElement);

    expect(mutate).toHaveBeenCalledWith({ auctionId: 2, on: false });
  });
});
