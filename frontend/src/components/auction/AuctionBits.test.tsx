import { render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it } from 'vitest';
import { AuctionCard } from './AuctionBits';

const auction = {
  auctionId: 1,
  productName: '빈티지 카메라',
  categoryName: '카메라',
  currentPrice: 10_000,
  bidCount: 0,
  status: 'READY' as const,
  startAt: '2099-01-01T10:00:00',
  endAt: '2099-01-02T10:00:00',
  live: false,
};

function renderCard(promoted: boolean) {
  render(
    <MemoryRouter>
      <AuctionCard auction={{ ...auction, promoted }} />
    </MemoryRouter>,
  );
}

describe('AuctionCard', () => {
  it('위로 올린 경매에는 홍보 표시를 붙인다', () => {
    renderCard(true);
    expect(screen.getByText('홍보')).toBeInTheDocument();
  });

  it('위로 올리지 않은 경매에는 홍보 표시가 없다', () => {
    renderCard(false);
    expect(screen.queryByText('홍보')).not.toBeInTheDocument();
  });
});
