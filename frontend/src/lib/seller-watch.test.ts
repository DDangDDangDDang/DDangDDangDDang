import { describe, expect, it } from 'vitest';
import { sellerViewerPath, shouldOpenStudio } from './seller-watch';

const live = { id: 7, status: 'LIVE', sellerId: 1 };
const none = new URLSearchParams();

describe('내 방송 시청 화면 → 스튜디오', () => {
  it('송출하는 탭에서 방송 중인 내 방송을 시청자 경로로 열면 스튜디오로 보낸다', () => {
    expect(shouldOpenStudio(live, 1, 7, none)).toBe(true);
  });

  it('송출을 맡지 않은 다른 탭은 스튜디오로 보내지 않는다 (보내면 그 탭이 송출을 빼앗는다)', () => {
    expect(shouldOpenStudio(live, 1, undefined, none)).toBe(false);
    expect(shouldOpenStudio(live, 1, 8, none)).toBe(false);
  });

  it('스튜디오의 [시청 화면 열기]로 연 탭은 시청자 화면을 보여 준다', () => {
    const path = sellerViewerPath(7);
    expect(path).toMatch(/^\/lives\/7\?/);
    expect(shouldOpenStudio(live, 1, 7, new URLSearchParams(path.split('?')[1]))).toBe(false);
  });

  it('다른 사람의 방송, 로그인하지 않은 손님, 방송 중이 아닌 내 방송은 그대로 시청 화면이다', () => {
    expect(shouldOpenStudio(live, 2, 7, none)).toBe(false);
    expect(shouldOpenStudio(live, undefined, 7, none)).toBe(false);
    expect(shouldOpenStudio({ ...live, status: 'SCHEDULED' }, 1, 7, none)).toBe(false);
    expect(shouldOpenStudio({ ...live, status: 'ENDED' }, 1, 7, none)).toBe(false);
    expect(shouldOpenStudio(undefined, 1, 7, none)).toBe(false);
  });
});
