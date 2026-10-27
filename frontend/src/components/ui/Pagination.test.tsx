import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { describe, expect, it, vi } from 'vitest';
import { pageWindow } from '@/lib/pagination';
import { Pagination } from './Pagination';

describe('pageWindow', () => {
  it('지금 페이지를 가운데에 둔다', () => {
    expect(pageWindow(5, 20)).toEqual([3, 4, 5, 6, 7]);
  });

  it('처음·끝에서는 범위 안에서 채운다', () => {
    expect(pageWindow(0, 20)).toEqual([0, 1, 2, 3, 4]);
    expect(pageWindow(19, 20)).toEqual([15, 16, 17, 18, 19]);
    expect(pageWindow(1, 3)).toEqual([0, 1, 2]);
    expect(pageWindow(0, 0)).toEqual([]);
  });
});

describe('Pagination', () => {
  it('페이지가 하나면 그리지 않는다', () => {
    const { container } = render(<Pagination page={0} totalPages={1} onChange={() => {}} label="목록 페이지" />);
    expect(container).toBeEmptyDOMElement();
  });

  it('번호를 누르면 그 페이지로, 첫 페이지에서는 이전 버튼이 꺼진다', async () => {
    const onChange = vi.fn();
    render(<Pagination page={0} totalPages={3} onChange={onChange} label="목록 페이지" />);
    expect(screen.getByRole('button', { name: '이전 페이지' })).toBeDisabled();
    expect(screen.getByRole('button', { name: '1' })).toHaveAttribute('aria-current', 'page');
    await userEvent.click(screen.getByRole('button', { name: '3' }));
    expect(onChange).toHaveBeenCalledWith(2);
  });
});
