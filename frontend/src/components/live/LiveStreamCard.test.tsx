import { fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter } from 'react-router';
import { describe, expect, it, vi } from 'vitest';
import type { Schema } from '@/api';
import { LiveStreamCard } from './LiveStreamCard';

// 미리보기 연결(LiveKit)은 하지 않는다.
vi.mock('./use-live-preview', () => ({ useStreamPreview: () => ({}) }));

const card: Schema<'LiveStreamCard'> = {
  liveStreamId: 7,
  title: '오늘의 경매',
  status: 'LIVE',
  sellerNickname: '윤정민',
  startedAt: new Date().toISOString(),
  viewerCount: 3,
  thumbnailUrl: 'https://cdn.test/live-thumbnails/7/a.jpg',
};

function renderCard(value: Schema<'LiveStreamCard'>) {
  const { container } = render(
    <MemoryRouter>
      <LiveStreamCard card={value} />
    </MemoryRouter>,
  );
  return { image: () => container.querySelector('img') };
}

describe('방송 카드 썸네일', () => {
  it('방송 중이면 송출 화면 썸네일을 보여 주고, 불러오지 못하면 판매자 이름 첫 글자로 돌아간다', () => {
    const { image } = renderCard(card);
    const thumbnail = image();
    expect(thumbnail).toHaveAttribute('src', card.thumbnailUrl);
    // 첫 글자는 아래 판매자 프로필에만 있다.
    expect(screen.getAllByText('윤')).toHaveLength(1);

    fireEvent.error(thumbnail as HTMLImageElement);
    expect(image()).toBeNull();
    expect(screen.getAllByText('윤')).toHaveLength(2);
  });

  it('썸네일이 없거나 방송이 끝났으면 판매자 이름 첫 글자를 보여 준다', () => {
    const { image } = renderCard({ ...card, thumbnailUrl: undefined });
    expect(image()).toBeNull();
  });

  it('끝난 방송은 마지막 썸네일이 있어도 보여 주지 않는다', () => {
    const { image } = renderCard({ ...card, status: 'ENDED' });
    expect(image()).toBeNull();
  });
});

describe('방송 카드 판매자 프로필', () => {
  it('판매자 프로필 이미지가 있으면 첫 글자 대신 이미지를 보여 준다', () => {
    const { container } = render(
      <MemoryRouter>
        <LiveStreamCard card={{ ...card, thumbnailUrl: undefined, status: 'ENDED', sellerProfileImageUrl: 'https://cdn.test/profile-images/1/a.png' }} />
      </MemoryRouter>,
    );
    expect(container.querySelector('img[src="https://cdn.test/profile-images/1/a.png"]')).not.toBeNull();
    // 썸네일 자리만 첫 글자이고, 아래 판매자 프로필은 이미지다.
    expect(screen.getAllByText('윤')).toHaveLength(1);
  });
});
