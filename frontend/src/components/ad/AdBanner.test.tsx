import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { AdBanner } from './AdBanner';

describe('AdBanner', () => {
  it('걸린 광고가 없으면 광고 자리와 권장 이미지 크기를 보여준다', () => {
    render(<AdBanner placement="home-top" />);
    const slot = screen.getByRole('note', { name: '광고 자리' });
    expect(slot).toHaveTextContent('2048 × 256');
    expect(screen.queryByRole('link')).not.toBeInTheDocument();
  });

  it('광고가 있으면 "광고" 표시와 함께 광고주 링크를 새 탭으로 연다', () => {
    render(<AdBanner placement="sidebar" ad={{ imageUrl: '/ad.png', linkUrl: 'https://example.com', alt: '가을 세일' }} />);
    const link = screen.getByRole('link', { name: /가을 세일/ });
    expect(link).toHaveAttribute('href', 'https://example.com');
    expect(link).toHaveAttribute('target', '_blank');
    expect(link).toHaveAttribute('rel', 'noopener noreferrer sponsored');
    expect(link).toHaveTextContent('광고');
  });
});
