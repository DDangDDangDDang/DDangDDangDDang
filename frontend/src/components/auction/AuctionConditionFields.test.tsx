import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { EMPTY_AUCTION_CONDITION, type AuctionConditionValues } from '@/lib/auction';
import { AuctionConditionFields } from './AuctionConditionFields';

function Harness() {
  const [values, setValues] = useState<AuctionConditionValues>({ ...EMPTY_AUCTION_CONDITION, startPrice: '10000', bidUnit: '1000' });
  return <AuctionConditionFields mode="live" values={values} errors={{}} onChange={(patch) => setValues((v) => ({ ...v, ...patch }))} />;
}

describe('AuctionConditionFields', () => {
  it('[즉시구매만 판매]를 켜면 시작가·입찰 단위가 0으로 잠기고, 끄면 입력했던 값이 돌아온다', async () => {
    render(<Harness />);
    const toggle = screen.getByRole('button', { name: '즉시구매만 판매' });

    await userEvent.click(toggle);
    expect(toggle).toHaveAttribute('aria-pressed', 'true');
    expect(screen.getByLabelText('시작가')).toBeDisabled();
    expect(screen.getByLabelText('시작가')).toHaveValue('0');
    expect(screen.getByLabelText('최소 입찰 단위')).toHaveValue('0');
    expect(screen.getByLabelText('즉시구매가')).toBeEnabled();

    await userEvent.click(toggle);
    expect(screen.getByLabelText('시작가')).toBeEnabled();
    expect(screen.getByLabelText('시작가')).toHaveValue('10,000');
    expect(screen.getByLabelText('최소 입찰 단위')).toHaveValue('1,000');
  });
});
