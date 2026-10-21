import { describe, expect, it } from 'vitest';
import { mediaErrorMessage } from './media';

describe('mediaErrorMessage', () => {
  const error = (name: string) => Object.assign(new Error(name), { name });

  it('권한 거부·장치 없음·사용 중을 구분한다', () => {
    expect(mediaErrorMessage('camera', error('NotAllowedError'))).toContain('카메라 사용이 막혀 있어요');
    expect(mediaErrorMessage('microphone', error('NotFoundError'))).toContain('연결된 마이크를 찾지 못했어요');
    expect(mediaErrorMessage('camera', error('NotReadableError'))).toContain('다른 프로그램이 카메라를 쓰고 있어요');
  });

  it('모르는 오류는 다시 시도 안내', () => {
    expect(mediaErrorMessage('microphone', 'oops')).toBe('마이크를 켜지 못했어요. 다시 시도해 주세요.');
  });
});
