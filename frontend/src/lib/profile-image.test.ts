import { describe, expect, it } from 'vitest';
import { PROFILE_IMAGE_MAX_BYTES, profileImageProblem } from './profile-image';

describe('profileImageProblem', () => {
  it('JPG·PNG·WEBP·GIF이고 5MB 이하면 올릴 수 있다', () => {
    expect(profileImageProblem({ type: 'image/png', size: 1000 })).toBeUndefined();
    expect(profileImageProblem({ type: 'image/jpeg', size: PROFILE_IMAGE_MAX_BYTES })).toBeUndefined();
  });

  it('다른 형식(SVG 등)은 막는다', () => {
    expect(profileImageProblem({ type: 'image/svg+xml', size: 1000 })).toMatch(/JPG, PNG/);
  });

  it('5MB를 넘거나 빈 파일이면 막는다', () => {
    expect(profileImageProblem({ type: 'image/png', size: PROFILE_IMAGE_MAX_BYTES + 1 })).toMatch(/5MB/);
    expect(profileImageProblem({ type: 'image/png', size: 0 })).toMatch(/빈 파일/);
  });
});
