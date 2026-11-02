import { fireEvent, render, screen } from '@testing-library/react';
import { useState } from 'react';
import { beforeEach, describe, expect, it } from 'vitest';
import { SellerTermsAgreement, type TermsAgreement } from './SellerTermsAgreement';

function Harness() {
  const [value, setValue] = useState<TermsAgreement>({ terms: false, privacy: false });
  return <SellerTermsAgreement value={value} onChange={setValue} />;
}

describe('SellerTermsAgreement', () => {
  beforeEach(() => {
    // jsdom에는 <dialog>의 showModal·close가 없다.
    HTMLDialogElement.prototype.showModal ??= function (this: HTMLDialogElement) {
      this.open = true;
    };
    HTMLDialogElement.prototype.close ??= function (this: HTMLDialogElement) {
      this.open = false;
    };
  });

  it('모두 동의하면 두 필수 항목이 함께 체크되고, 하나를 풀면 모두 동의도 풀린다', () => {
    render(<Harness />);
    const all = screen.getByRole('checkbox', { name: '모두 동의해요' });
    const terms = screen.getByRole('checkbox', { name: /판매자 이용약관에 동의해요/ });
    const privacy = screen.getByRole('checkbox', { name: /개인정보 수집·이용에 동의해요/ });

    fireEvent.click(all);
    expect(terms).toBeChecked();
    expect(privacy).toBeChecked();

    fireEvent.click(privacy);
    expect(all).not.toBeChecked();
    expect(terms).toBeChecked();
  });

  it('전문 창에서 [동의하고 닫기]를 누르면 그 항목만 동의된다', () => {
    render(<Harness />);
    fireEvent.click(screen.getByRole('button', { name: '판매자 이용약관 전문 보기' }));
    expect(screen.getByText('제1조 (목적)')).toBeInTheDocument();

    fireEvent.click(screen.getByRole('button', { name: '동의하고 닫기' }));

    expect(screen.getByRole('checkbox', { name: /판매자 이용약관에 동의해요/ })).toBeChecked();
    expect(screen.getByRole('checkbox', { name: /개인정보 수집·이용에 동의해요/ })).not.toBeChecked();
  });
});
