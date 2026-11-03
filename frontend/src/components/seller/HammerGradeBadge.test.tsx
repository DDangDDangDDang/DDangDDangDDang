import { render, screen } from '@testing-library/react';
import { describe, expect, it } from 'vitest';
import { HammerGradeBadge } from './HammerGradeBadge';

describe('망치 등급 배지', () => {
  it.each([
    ['NEWBIE', '새 망치'],
    ['WOOD', '나무 망치'],
    ['STONE', '돌 망치'],
    ['SILVER', '은 망치'],
    ['GOLD', '금 망치'],
    ['DIAMOND', '다이아 망치'],
  ] as const)('%s 등급은 "%s"로 보여 준다', (grade, label) => {
    render(<HammerGradeBadge grade={grade} />);

    expect(screen.getByText(label)).toBeInTheDocument();
  });

  it('등급이 무엇으로 정해지는지 설명을 붙인다', () => {
    render(<HammerGradeBadge grade="GOLD" />);

    expect(screen.getByText('금 망치').closest('[title]')).toHaveAttribute('title', expect.stringContaining('최근 12개월'));
  });

  it('등급이 없으면 아무것도 보여 주지 않는다', () => {
    const { container } = render(<HammerGradeBadge grade={undefined} />);

    expect(container).toBeEmptyDOMElement();
  });
});
