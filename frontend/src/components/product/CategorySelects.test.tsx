import { render, screen } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { useState } from 'react';
import { describe, expect, it } from 'vitest';
import { CategorySelects } from './CategorySelects';

const categories = [
  { id: 1, name: '카메라', children: [{ id: 11, name: '필름 카메라' }, { id: 12, name: '렌즈' }] },
  { id: 2, name: '시계', children: [{ id: 21, name: '손목시계' }] },
  { id: 3, name: '빈 대분류', children: [] },
];

function Harness({ mode, initial = '' }: { mode: 'leaf' | 'filter'; initial?: string }) {
  const [value, setValue] = useState(initial);
  return (
    <>
      <CategorySelects categories={categories} mode={mode} labels={['카테고리', '세부 카테고리']} value={value} onChange={setValue} />
      <output>{value || '없음'}</output>
      <button type="button" onClick={() => setValue('')}>
        비우기
      </button>
    </>
  );
}

describe('CategorySelects', () => {
  it('상품 등록: 대분류를 고르면 그 소분류만 고를 수 있고, 소분류까지 골라야 값이 생긴다', async () => {
    render(<Harness mode="leaf" />);
    const parent = screen.getByLabelText('카테고리');
    const child = screen.getByLabelText('세부 카테고리');
    expect(child).toBeDisabled();
    // 소분류가 없는 대분류는 고를 수 없다
    expect(screen.queryByRole('option', { name: '빈 대분류' })).toBeNull();

    await userEvent.selectOptions(parent, '1');
    expect(screen.getByRole('status')).toHaveTextContent('없음');
    expect(child).toBeEnabled();
    expect([...(child as HTMLSelectElement).options].map((o) => o.text)).toEqual(['소분류 선택', '필름 카메라', '렌즈']);

    await userEvent.selectOptions(child, '12');
    expect(screen.getByRole('status')).toHaveTextContent('12');

    // 대분류를 바꾸면 소분류는 다시 고른다
    await userEvent.selectOptions(parent, '2');
    expect(screen.getByRole('status')).toHaveTextContent('없음');
    expect(child).toHaveValue('');
  });

  it('상품 수정: 저장된 소분류로 대분류가 채워진다', () => {
    render(<Harness mode="leaf" initial="21" />);
    expect(screen.getByLabelText('카테고리')).toHaveValue('2');
    expect(screen.getByLabelText('세부 카테고리')).toHaveValue('21');
  });

  it('목록 거르기: 대분류만 골라도 그 대분류로, 소분류 "전체"는 다시 대분류로', async () => {
    render(<Harness mode="filter" />);
    await userEvent.selectOptions(screen.getByLabelText('카테고리'), '1');
    expect(screen.getByRole('status')).toHaveTextContent('1');
    await userEvent.selectOptions(screen.getByLabelText('세부 카테고리'), '11');
    expect(screen.getByRole('status')).toHaveTextContent('11');
    await userEvent.selectOptions(screen.getByLabelText('세부 카테고리'), '');
    expect(screen.getByRole('status')).toHaveTextContent('1');
  });

  it('목록 거르기: 밖에서 값을 비우면 대분류도 "전체 카테고리"로 돌아간다', async () => {
    render(<Harness mode="filter" />);
    await userEvent.selectOptions(screen.getByLabelText('카테고리'), '1');
    await userEvent.click(screen.getByRole('button', { name: '비우기' }));
    expect(screen.getByLabelText('카테고리')).toHaveValue('');
    expect(screen.getByLabelText('세부 카테고리')).toBeDisabled();
  });
});
